function [] = plotAvgVmUtilization()

    plotGenericLine(2, 8, 'Average VM Utilization (%)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(2, 8, {'Average VM Utilization';'for Danger Assessment App (%)'}, 'DANGER_ASSESSMENT', '', 'NorthWest');
    plotGenericLine(2, 8, 'Average VM Utilization for Navigation App (%)', 'TRAFFIC_MANAGEMENT', '', 'NorthWest');
    plotGenericLine(2, 8, 'Average VM Utilization for Infotainment App (%)', 'INFOTAINMENT', '', 'NorthWest');

end