package com.farm.customer.ui

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
import com.farm.customer.browse.filterListings
import com.farm.customer.browse.sortListings
import com.farm.customer.land.LandFilterState
import com.farm.customer.land.LandRepository
import com.farm.customer.land.PlotItem
import com.farm.customer.land.PlotSeasonItem
import com.farm.customer.land.ReservationItem
import com.farm.customer.land.filterPlots
import com.farm.customer.land.sortPlots
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class HomeTab { PRODUCE, LAND }
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
    var produceError by mutableStateOf<String?>(null)
        private set
    var produceToast by mutableStateOf<String?>(null)
        private set

    val listings: List<ProduceListing> get() = sortListings(filterListings(allListings, filters), filters.sort)

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
    var landError by mutableStateOf<String?>(null)
        private set
    var landToast by mutableStateOf<String?>(null)
        private set
    var reservingPlot by mutableStateOf<PlotItem?>(null)
    var timelineReservation by mutableStateOf<ReservationItem?>(null)

    val plots: List<PlotItem> get() = sortPlots(filterPlots(allPlots, landFilters), landFilters.sort)

    private var produceJob: Job? = null
    private var landJob: Job? = null

    init {
        refreshProduce()
        refreshLand()
    }

    fun changeLocation(loc: LocationCoords) {
        location = loc
        refreshProduce()
        refreshLand()
    }

    /** Server-side filters (radius, crop, organic) re-query; the rest filter on the device. */
    fun updateFilters(next: FilterState) {
        val refetch = next.radiusKm != filters.radiusKm || next.crop != filters.crop || next.organicOnly != filters.organicOnly
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
            try {
                allListings = BrowseRepository.fetchNearby(
                    location.latitude, location.longitude, filters.radiusKm, filters.crop,
                    if (filters.organicOnly) true else null,
                )
            } catch (e: Exception) {
                produceError = e.message ?: "Failed to load produce listings"
            } finally {
                produceLoading = false
            }
        }
    }

    fun onAddedToCart(item: ProduceListing) = toast(true, "Added 1 ${item.unit} ${item.cropName} to cart 🛒")

    private fun toast(produce: Boolean, msg: String) {
        viewModelScope.launch {
            if (produce) produceToast = msg else landToast = msg
            delay(if (produce) 3000 else 4000)
            if (produce) { if (produceToast == msg) produceToast = null } else if (landToast == msg) landToast = null
        }
    }

    fun updateLandFilters(next: LandFilterState) {
        val refetch = next.radiusKm != landFilters.radiusKm
        landFilters = next
        if (refetch) refreshLand()
    }

    fun refreshLand() {
        landJob?.cancel()
        landJob = viewModelScope.launch {
            landLoading = true
            landError = null
            try {
                val p = async { LandRepository.fetchPlots(location.latitude, location.longitude, landFilters.radiusKm) }
                val r = async { LandRepository.fetchReservations() }
                allPlots = p.await()
                reservations = r.await()
            } catch (e: Exception) {
                landError = e.message ?: "Failed to load land data"
            } finally {
                landLoading = false
            }
        }
    }

    suspend fun createReservation(plot: PlotItem, season: PlotSeasonItem, cropId: String, qtyKg: Double) {
        val created = LandRepository.submitReservation(plot, season, cropId, qtyKg)
        reservations = listOf(created) + reservations
        toast(false, "✓ Reservation submitted for ${created.cropName}! Farmer notified.")
        landSubTab = LandSubTab.MY_RESERVATIONS
        timelineReservation = created
    }
}
