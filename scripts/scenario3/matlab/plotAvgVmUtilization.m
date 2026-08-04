function [] = plotAvgVmUtilization()

    plotGenericLine(2, 8, 'Average VM Utilization of Edge (%)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(2, 8, {'Average VM Utilization of Edge';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', '', 'NorthWest');
    plotGenericLine(2, 8, {'Average VM Utilization of Edge';'Average VM Utilization  for Health App (%)'}, 'HEALTH_APP', '', 'NorthWest');
    plotGenericLine(2, 8, {'Average VM Utilization of Edge';'Average VM Utilization for Infotainment App (%)'}, 'INFOTAINMENT_APP', '', 'NorthWest');

    plotGenericLine(3, 8, 'Average VM Utilization of Cloud (%)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(3, 8, {'Average VM Utilization of Cloud';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', '', 'NorthWest');
    plotGenericLine(3, 8, {'Average VM Utilization of Cloud';'Average VM Utilization  for Health App (%)'}, 'HEALTH_APP', '', 'NorthWest');
    plotGenericLine(3, 8, {'Average VM Utilization of Cloud';'Average VM Utilization for Infotainment App (%)'}, 'INFOTAINMENT_APP', '', 'NorthWest');

end