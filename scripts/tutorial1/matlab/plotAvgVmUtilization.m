function [] = plotAvgVmUtilization()

    plotGenericLine(2, 8, 'Average VM Utilization (%)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(2, 8, {'Average VM Utilization';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', '', 'NorthWest');
    plotGenericLine(2, 8, 'Average VM Utilization for Health App (%)', 'HEALTH_APP', '', 'NorthWest');
    plotGenericLine(2, 8, 'Average VM Utilization for Infotainment App (%)', 'INFOTAINMENT_APP', '', 'NorthWest');

end