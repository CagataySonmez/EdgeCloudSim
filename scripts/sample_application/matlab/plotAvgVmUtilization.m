function [] = plotAvgVmUtilization()

    plotGenericResult(1, 8, 'Average VM Utilization (%)', 'ALL_APPS', 0);
    plotGenericResult(1, 8, {'Average VM Utilization';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 0);
    plotGenericResult(1, 8, 'Average VM Utilization for Health App (%)', 'HEALTH_APP', 0);
    plotGenericResult(1, 8, 'Average VM Utilization for Infotainment App (%)', 'INFOTAINMENT_APP', 0);
    plotGenericResult(1, 8, 'Average VM Utilization for Heavy Comp. App (%)', 'HEAVY_COMP_APP', 0);
    
end