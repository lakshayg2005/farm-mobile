import type { PlotItem, LandFilterState, CropStage, LandSortOption } from './types.ts';

/**
 * Filter plots based on user criteria:
 * - Text search query (title, description, farmerName, village, district, allowed crops)
 * - Season selection
 * - Crop selection
 * - Soil type
 * - Search radius (km)
 * - Area in acres range
 */
export function filterPlots(plots: PlotItem[], filters: LandFilterState): PlotItem[] {
  return plots.filter((plot) => {
    // 1. Text Query
    if (filters.query.trim()) {
      const q = filters.query.trim().toLowerCase();
      const matchTitle = plot.title.toLowerCase().includes(q);
      const matchDesc = plot.description?.toLowerCase().includes(q) ?? false;
      const matchFarmer = plot.farmerName.toLowerCase().includes(q);
      const matchDistrict = plot.district.toLowerCase().includes(q);
      const matchVillage = plot.village.toLowerCase().includes(q);
      const matchCrops = plot.seasons.some((s) =>
        s.allowedCrops.some((c) => c.toLowerCase().includes(q)),
      );

      if (!matchTitle && !matchDesc && !matchFarmer && !matchDistrict && !matchVillage && !matchCrops) {
        return false;
      }
    }

    // 2. Season
    if (filters.season && filters.season !== 'ALL') {
      const hasSeason = plot.seasons.some((s) => s.season.toLowerCase() === filters.season?.toLowerCase());
      if (!hasSeason) return false;
    }

    // 3. Crop
    if (filters.crop && filters.crop !== 'ALL') {
      const cropQuery = filters.crop.toLowerCase();
      const hasCrop = plot.seasons.some((s) =>
        s.allowedCrops.some((c) => c.toLowerCase() === cropQuery),
      );
      if (!hasCrop) return false;
    }

    // 4. Soil Type
    if (filters.soilType && filters.soilType !== 'ALL') {
      if (plot.soilType.toLowerCase() !== filters.soilType.toLowerCase()) {
        return false;
      }
    }

    // 5. Radius in km
    if (filters.radiusKm > 0) {
      const maxMetres = filters.radiusKm * 1000;
      if (plot.distanceM > maxMetres) {
        return false;
      }
    }

    // 6. Min Acres
    if (filters.minAcres !== null && filters.minAcres !== undefined && filters.minAcres > 0) {
      if (plot.areaAcres < filters.minAcres) {
        return false;
      }
    }

    // 7. Max Acres
    if (filters.maxAcres !== null && filters.maxAcres !== undefined && filters.maxAcres > 0) {
      if (plot.areaAcres > filters.maxAcres) {
        return false;
      }
    }

    return true;
  });
}

/**
 * Sort plots:
 * - 'distance': Nearest first
 * - 'acres_desc': Largest acreage first
 * - 'acres_asc': Smallest acreage first
 * - 'price_asc': Lowest starting price first
 * - 'price_desc': Highest starting price first
 */
export function sortPlots(plots: PlotItem[], sort: LandSortOption): PlotItem[] {
  const copy = [...plots];
  switch (sort) {
    case 'distance':
      return copy.sort((a, b) => a.distanceM - b.distanceM);
    case 'acres_desc':
      return copy.sort((a, b) => b.areaAcres - a.areaAcres);
    case 'acres_asc':
      return copy.sort((a, b) => a.areaAcres - b.areaAcres);
    case 'price_asc':
      return copy.sort((a, b) => {
        const minA = Math.min(...a.seasons.map((s) => s.pricePerKg), 999);
        const minB = Math.min(...b.seasons.map((s) => s.pricePerKg), 999);
        return minA - minB;
      });
    case 'price_desc':
      return copy.sort((a, b) => {
        const maxA = Math.max(...a.seasons.map((s) => s.pricePerKg), 0);
        const maxB = Math.max(...b.seasons.map((s) => s.pricePerKg), 0);
        return maxB - maxA;
      });
    default:
      return copy;
  }
}

/**
 * Calculate deposit (20%) and total estimated reservation cost.
 */
export function calculateReservationEstimate(
  pricePerKg: number,
  expectedQtyKg: number,
): { deposit: number; total: number } {
  const safeQty = Math.max(0, expectedQtyKg);
  const total = Math.round(pricePerKg * safeQty * 100) / 100;
  const deposit = Math.round(total * 0.2 * 100) / 100; // standard 20% deposit
  return { deposit, total };
}

/**
 * Visual styling and metadata for crop growth timeline stages.
 */
export function getStageMetadata(stage: CropStage): {
  label: string;
  icon: string;
  color: string;
  bgColor: string;
  stepIndex: number;
} {
  switch (stage) {
    case 'SOWN':
      return {
        label: 'Sown / Germinating',
        icon: '🌱',
        color: '#15803d',
        bgColor: '#dcfce7',
        stepIndex: 1,
      };
    case 'GROWING':
      return {
        label: 'Vegetative Growth',
        icon: '🌿',
        color: '#0369a1',
        bgColor: '#e0f2fe',
        stepIndex: 2,
      };
    case 'FLOWERING':
      return {
        label: 'Flowering & Fruiting',
        icon: '🌸',
        color: '#b45309',
        bgColor: '#fef3c7',
        stepIndex: 3,
      };
    case 'HARVEST_READY':
      return {
        label: 'Harvest Ready',
        icon: '🌾',
        color: '#7e22ce',
        bgColor: '#f3e8ff',
        stepIndex: 4,
      };
    default:
      return {
        label: stage,
        icon: '📍',
        color: '#475569',
        bgColor: '#f1f5f9',
        stepIndex: 0,
      };
  }
}
