function [] = plotAvgFailedTask()

     plotGenericLine(1, 2, 'Average Failed Tasks (%)', 'ALL_APPS', 'NorthWest', 1);
     plotGenericLine(1, 2, 'Failed Tasks for Danger Assessment App (%)', 'DANGER_ASSESSMENT', 'NorthWest', 1);
     plotGenericLine(1, 2, 'Failed Tasks for Navigation App (%)', 'TRAFFIC_MANAGEMENT', 'NorthWest', 1);
     plotGenericLine(1, 2, 'Failed Tasks for Infotainment App (%)', 'INFOTAINMENT', 'NorthWest', 1);
     
end