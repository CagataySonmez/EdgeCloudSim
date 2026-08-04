function [] = plotAvgVmUtilization()

    plotGenericLine(2, 8, 'Average VM Utilization of Edge (%)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(2, 8, {'Average VM Utilization of Edge';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', '', 'NorthWest');
    plotGenericLine(2, 8, {'Average VM Utilization of Edge';'for Health App (%)'}, 'HEALTH_APP', '', 'NorthWest');
    plotGenericLine(2, 8, {'Average VM Utilization of Edge';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', '', 'NorthWest');

    plotGenericLine(4, 8, 'Average VM Utilization of Mobile (%)', 'ALL_APPS', '', 'SouthEast');
    plotGenericLine(4, 8, {'Average VM Utilization of Mobile';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', '', 'SouthEast');
    plotGenericLine(4, 8, {'Average VM Utilization of Mobile';'for Health App (%)'}, 'HEALTH_APP', '', 'SouthEast');
    plotGenericLine(4, 8, {'Average VM Utilization of Mobile';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', '', 'SouthEast');
end