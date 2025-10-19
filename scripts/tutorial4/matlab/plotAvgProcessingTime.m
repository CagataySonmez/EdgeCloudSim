function [] = plotAvgProcessingTime()

    plotGenericLine(1, 6, 'Processing Time (sec)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(1, 6, 'Processing Time for Danger Assessment App (sec)', 'DANGER_ASSESSMENT', '', 'NorthWest');
    plotGenericLine(1, 6, 'Processing Time for Navigation App (sec)', 'TRAFFIC_MANAGEMENT', '', 'NorthWest');
    plotGenericLine(1, 6, 'Processing Time for Infotainment App (sec)', 'INFOTAINMENT', '', 'NorthWest');

    plotGenericLine(2, 6, 'Processing Time on Edge (sec)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(2, 6, {'Processing Time on Edge';'for Danger Assessment App (sec)'}, 'DANGER_ASSESSMENT', '', 'NorthWest');
    plotGenericLine(2, 6, {'Processing Time on Edge';'for Navigation App (sec)'}, 'TRAFFIC_MANAGEMENT', '', 'NorthWest');
    plotGenericLine(2, 6, {'Processing Time on Edge';'for Infotainment App (sec)'}, 'INFOTAINMENT', '', 'NorthWest');

    plotGenericLine(3, 6, 'Processing Time on Cloud (sec)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(3, 6, {'Processing Time on Cloud';'for Danger Assessment App (sec)'}, 'DANGER_ASSESSMENT', '', 'NorthWest');
    plotGenericLine(3, 6, {'Processing Time on Cloud';'for Navigation App (sec)'}, 'TRAFFIC_MANAGEMENT', '', 'NorthWest');
    plotGenericLine(3, 6, {'Processing Time on Cloud';'for Infotainment App (sec)'}, 'INFOTAINMENT', '', 'NorthWest');
    
end