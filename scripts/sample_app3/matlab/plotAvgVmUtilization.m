function [] = plotAvgVmUtilization()

    plotGenericResult(2, 8, 'Average VM Utilization of Edge (%)', 'ALL_APPS', '');
    plotGenericResult(2, 8, {'Average VM Utilization of Edge';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', '');
    plotGenericResult(2, 8, {'Average VM Utilization of Edge';'for Health App (%)'}, 'HEALTH_APP', '');
    plotGenericResult(2, 8, {'Average VM Utilization of Edge';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', '');
    plotGenericResult(2, 8, {'Average VM Utilization of Edge';'for Heavy Comp. App (%)'}, 'HEAVY_COMP_APP', '');

    plotGenericResult(4, 8, 'Average VM Utilization of Mobile(%)', 'ALL_APPS', '');
    plotGenericResult(4, 8, {'Average VM Utilization of Mobile';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', '');
    plotGenericResult(4, 8, {'Average VM Utilization of Mobile';'for Health App (%)'}, 'HEALTH_APP', '');
    plotGenericResult(4, 8, {'Average VM Utilization of Mobile';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', '');
    plotGenericResult(4, 8, {'Average VM Utilization of Mobile';'for Heavy Comp. App (%)'}, 'HEAVY_COMP_APP', '');
end