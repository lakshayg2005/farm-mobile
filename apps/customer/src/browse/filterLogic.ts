import type { ProduceListing, FilterState } from './types.ts';

/**
 * Filter listings based on user filter criteria:
 * - Query (matches cropName, variety, farmName, village, district)
 * - Crop filter (exact match on cropId)
 * - Radius in km (distanceM <= radiusKm * 1000)
 * - Organic only
 * - Minimum grade ('A' requires A; 'B' requires A or B; 'C' requires A, B, or C; 'ALL' includes any)
 * - Min/Max price per unit
 */
export function filterListings(listings: ProduceListing[], filters: FilterState): ProduceListing[] {
  return listings.filter((item) => {
    // 1. Text Query
    if (filters.query.trim()) {
      const q = filters.query.trim().toLowerCase();
      const matchName = item.cropName.toLowerCase().includes(q);
      const matchVariety = item.variety?.toLowerCase().includes(q) ?? false;
      const matchFarm = item.farmName.toLowerCase().includes(q);
      const matchDistrict = item.district.toLowerCase().includes(q);
      const matchVillage = item.village?.toLowerCase().includes(q) ?? false;
      if (!matchName && !matchVariety && !matchFarm && !matchDistrict && !matchVillage) {
        return false;
      }
    }

    // 2. Crop
    if (filters.crop && filters.crop !== 'ALL') {
      if (item.cropId.toLowerCase() !== filters.crop.toLowerCase()) {
        return false;
      }
    }

    // 3. Radius
    if (filters.radiusKm > 0) {
      const maxMetres = filters.radiusKm * 1000;
      if (item.distanceM > maxMetres) {
        return false;
      }
    }

    // 4. Organic only
    if (filters.organicOnly && !item.isOrganic) {
      return false;
    }

    // 5. Minimum Grade
    if (filters.minGrade && filters.minGrade !== 'ALL') {
      if (!item.grade) return false;
      const rank: Record<string, number> = { A: 3, B: 2, C: 1 };
      const itemRank = rank[item.grade] ?? 0;
      const requiredRank = rank[filters.minGrade] ?? 0;
      if (itemRank < requiredRank) {
        return false;
      }
    }

    // 6. Min Price
    if (filters.minPrice !== null && filters.minPrice !== undefined && filters.minPrice > 0) {
      if (item.pricePerUnit < filters.minPrice) {
        return false;
      }
    }

    // 7. Max Price
    if (filters.maxPrice !== null && filters.maxPrice !== undefined && filters.maxPrice > 0) {
      if (item.pricePerUnit > filters.maxPrice) {
        return false;
      }
    }

    return true;
  });
}

/**
 * Sort listings based on the chosen sort option:
 * - 'distance': Nearest first
 * - 'price_asc': Lowest price first
 * - 'price_desc': Highest price first
 * - 'harvest_date': Most recently harvested first
 */
export function sortListings(listings: ProduceListing[], sort: FilterState['sort']): ProduceListing[] {
  const copy = [...listings];
  switch (sort) {
    case 'distance':
      return copy.sort((a, b) => a.distanceM - b.distanceM);
    case 'price_asc':
      return copy.sort((a, b) => a.pricePerUnit - b.pricePerUnit);
    case 'price_desc':
      return copy.sort((a, b) => b.pricePerUnit - a.pricePerUnit);
    case 'harvest_date':
      return copy.sort((a, b) => new Date(b.harvestDate).getTime() - new Date(a.harvestDate).getTime());
    default:
      return copy;
  }
}
