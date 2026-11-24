package com.farm.customer.ui

import com.farm.customer.prefs.LocalLists

import com.farm.customer.browse.applyLocalFilters

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farm.customer.browse.BrowseRepository
import com.farm.customer.browse.FilterState
import com.farm.customer.browse.LocationCoords
import com.farm.customer.browse.POPULAR_DISTRICTS
import com.farm.customer.browse.ProduceListing
import com.farm.customer.browse.matchesQuery
import com.farm.customer.browse.produceVocabulary
import com.farm.customer.browse.suggestWords
import com.farm.customer.browse.needsRefetch
import com.farm.core.api.CatalogApi
import com.farm.core.api.CropOut
import com.farm.customer.land.LandFilterState
import com.farm.customer.land.LandRepository
import com.farm.customer.land.PlotItem
import com.farm.customer.land.PlotSeasonItem
import com.farm.customer.land.ReservationItem
import com.farm.customer.land.filterPlots
import com.farm.customer.land.sortPlots
import com.farm.core.api.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The bottom-bar destinations of the customer app. */
enum class HomeTab { PRODUCE, LAND, ORDERS, PROFILE }
enum class LandSubTab { EXPLORE, MY_RESERVATIONS }

/** State of the home screen: the Produce tab (useProduceBrowse) and the Land tab (useLand). */
class HomeViewModel : ViewModel() {
    var tab by mutableStateOf(HomeTab.PRODUCE)
    var location by mutableStateOf(POPULAR_DISTRICTS[0])
        private set

    // Produce
    var filters by mutableStateOf(FilterState())
        private set
    var produceMap by mutableStateOf(false)
    private var allListings by mutableStateOf<List<ProduceListing>>(emptyList())
    var produceLoading by mutableStateOf(true)
        private set
    /** Why the last search failed (shown with Retry, and "Change server" when offline). */
    var produceError by mutableStateOf<Throwable?>(null)
        private set
    var produceToast by mutableStateOf<String?>(null)
        private set

    /** Words that complete what is typed in the produce search ("apl" -> "Apple"), best first. */
    val suggestions: List<String>
        get() = suggestWords(filters.query, produceVocabulary(allListings, crops.map { it.name })).map { it.text }

    /** The loaded results, narrowed by the text box (the server applies every other filter). */
    val listings: List<ProduceListing>
        get() = applyLocalFilters(allListings, filters, LocalLists.favourites.value.map { it.farmId }.toSet())
    /** Cursor of the next result page; null when everything is loaded. */
    var produceNextCursor by mutableStateOf<String?>(null)
        private set
    var loadingMoreProduce by mutableStateOf(false)
        private set
    var produceMoreError by mutableStateOf<Throwable?>(null)
        private set
    /** Crop chips, from GET /catalog/crops; empty (only "All") when it cannot be loaded. */
    var crops by mutableStateOf<List<CropOut>>(emptyList())
        private set

    // Land
    var landFilters by mutableStateOf(LandFilterState())
        private set
    var landMap by mutableStateOf(true)
    var landSubTab by mutableStateOf(LandSubTab.EXPLORE)
    private var allPlots by mutableStateOf<List<PlotItem>>(emptyList())
    var reservations by mutableStateOf<List<ReservationItem>>(emptyList())
        private set
    var landLoading by mutableStateOf(true)
        private set
    var landError by mutableStateOf<Throwable?>(null)
        private set
    var landToast by mutableStateOf<String?>(null)
        private set
    var reservationsError by mutableStateOf<Throwable?>(null)
        private set
    var reservingPlot by mutableStateOf<PlotItem?>(null)
    /** The season picked on the plot detail, preselected in the reservation sheet. */
    var reservingSeasonId by mutableStateOf<String?>(null)
    var detailPlot by mutableStateOf<PlotItem?>(null)
    var landNextOffset by mutableStateOf<Int?>(null)
        private set
    var loadingMorePlots by mutableStateOf(false)
        private set
    var landMoreError by mutableStateOf<Throwable?>(null)
        private set
    /** True when the list shows "search this area" results rather than the radius search. */
    var searchedArea by mutableStateOf(false)
        private set

    /** Every plot the last search returned, before the on-phone filters (for the crop choices). */
    val allLoadedPlots: List<PlotItem> get() = allPlots
    val plots: List<PlotItem> get() = sortPlots(filterPlots(allPlots, landFilters), landFilters.sort)

    private var produceJob: Job? = null
    private var landJob: Job? = null

    init {
        viewModelScope.launch { load { CatalogApi.listCrops() }.onSuccess { crops = it } }
        refreshProduce()
        refreshLand()
    }

    fun changeLocation(loc: LocationCoords) {
        location = loc
        refreshProduce()
        refreshLand()
    }

    /** Every filter but the text query is applied by the server, so changing one searches again. */
    fun updateFilters(next: FilterState) {
        val refetch = needsRefetch(filters, next)
        filters = next
        if (refetch) refreshProduce()
    }

    fun toggleCrop(cropId: String?) = updateFilters(filters.copy(crop = if (filters.crop == cropId) null else cropId))

    fun resetFilters() = updateFilters(FilterState())

    fun refreshProduce() {
        produceJob?.cancel()
        produceJob = viewModelScope.launch {
            produceLoading = true
            produceError = null
            produceMoreError = null
            load { BrowseRepository.search(location.latitude, location.longitude, filters) }
                .onSuccess { allListings = it.listings; produceNextCursor = it.nextCursor }
                .onFailure { allListings = emptyList(); produceNextCursor = null; produceError = it }
            produceLoading = false
        }
    }

    /** The next page of results (catalog cursor pagination). */
    fun loadMoreProduce() {
        val cursor = produceNextCursor ?: return
        if (loadingMoreProduce) return
        loadingMoreProduce = true
        produceMoreError = null
        viewModelScope.launch {
            load { BrowseRepository.search(location.latitude, location.longitude, filters, cursor) }
                .onSuccess { page ->
                    val known = allListings.map { it.id }.toSet()
                    allListings = allListings + page.listings.filter { it.id !in known }
                    produceNextCursor = page.nextCursor
                }
                .onFailure { produceMoreError = it }
            loadingMoreProduce = false
        }
    }

    fun onAddedToCart(item: ProduceListing) = toast(true, "Added 1 ${item.unit} ${item.cropName} to cart")

    private fun toast(produce: Boolean, msg: String) {
        viewModelScope.launch {
            if (produce) produceToast = msg else landToast = msg
            delay(if (produce) 3000 else 4000)
            if (produce) { if (produceToast == msg) produceToast = null } else if (landToast == msg) landToast = null
        }
    }

    /** Radius, season and crop are searched on the server; the text query filters on the device. */
    fun updateLandFilters(next: LandFilterState) {
        val refetch = next.radiusKm != landFilters.radiusKm || next.season != landFilters.season || next.crop != landFilters.crop
        landFilters = next
        if (refetch) refreshLand()
    }

    fun refreshLand() {
        landJob?.cancel()
        landJob = viewModelScope.launch {
            landLoading = true
            landError = null
            landMoreError = null
            val f = landFilters
            val plots = async { load { LandRepository.searchPlots(location.latitude, location.longitude, f.radiusKm, f.season, f.crop) } }
            val mine = async { load { LandRepository.fetchReservations() } }
            plots.await()
                .onSuccess { allPlots = it.plots; landNextOffset = it.nextOffset; searchedArea = false }
                .onFailure { allPlots = emptyList(); landNextOffset = null; landError = it }
            mine.await().onSuccess { reservations = it; reservationsError = null }
                .onFailure { reservations = emptyList(); reservationsError = it }
            landLoading = false
        }
    }

    /** The next page of plot-seasons around the chosen place. */
    fun loadMorePlots() {
        val offset = landNextOffset ?: return
        if (loadingMorePlots) return
        loadingMorePlots = true
        landMoreError = null
        viewModelScope.launch {
            val f = landFilters
            load { LandRepository.searchPlots(location.latitude, location.longitude, f.radiusKm, f.season, f.crop, offset, allPlots) }
                .onSuccess { allPlots = it.plots; landNextOffset = it.nextOffset }
                .onFailure { landMoreError = it }
            loadingMorePlots = false
        }
    }

    /** Plots inside the part of the map on screen (west, south, east, north). */
    fun searchMapArea(bbox: List<Double>) {
        landJob?.cancel()
        landJob = viewModelScope.launch {
            landLoading = true
            landError = null
            val f = landFilters
            load { LandRepository.searchArea(bbox, f.season, f.crop, location.latitude, location.longitude) }
                .onSuccess { allPlots = it.plots; landNextOffset = null; searchedArea = true }
                .onFailure { landError = it }
            landLoading = false
        }
    }

    /** Throws the server's error (the sheet shows it); on success the new reservation is listed. */
    suspend fun createReservation(plot: PlotItem, season: PlotSeasonItem, cropId: String, qtyKg: Double) {
        val created = LandRepository.submitReservation(plot, season, cropId, qtyKg)
        reservations = listOf(created) + reservations.filter { it.id != created.id }
        toast(false, "Reservation sent for ${created.cropName}. The farmer has 48 hours to answer.")
        landSubTab = LandSubTab.MY_RESERVATIONS
    }
}
