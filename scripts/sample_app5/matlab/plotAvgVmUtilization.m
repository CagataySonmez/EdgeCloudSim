function [] = plotAvgVmUtilization()

    plotGenericLine(2, 8, 'Average VM Utilization of RSU (%)', 'ALL_APPS', 'NorthWest', 0);

    plotGenericLine(3, 8, 'Average VM Utilization of Cloud (%)', 'ALL_APPS', 'NorthWest', 0);
    
end