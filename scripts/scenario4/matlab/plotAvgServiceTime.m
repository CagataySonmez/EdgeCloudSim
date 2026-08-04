function [] = plotAvgServiceTime()

    plotGenericLine(1, 5, 'Service Time (sec)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(1, 5, {'Service Time for';'Danger Assessment App (sec)'}, 'DANGER_ASSESSMENT', '', 'NorthWest');
    plotGenericLine(1, 5, 'Service Time for Navigation App (sec)', 'TRAFFIC_MANAGEMENT', '', 'NorthWest');
    plotGenericLine(1, 5, 'Service Time for Infotainment App (sec)', 'INFOTAINMENT', '', 'NorthWest');

    plotGenericLine(2, 5, 'Service Time on Edge (sec)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(2, 5, {'Service Time on Edge';'for Danger Assessment App (sec)'}, 'DANGER_ASSESSMENT', '', 'NorthWest');
    plotGenericLine(2, 5, 'Service Time on Edge for Navigation App (sec)', 'TRAFFIC_MANAGEMENT', '', 'NorthWest');
    plotGenericLine(2, 5, {'Service Time on Edge';'for Infotainment App (sec)'}, 'INFOTAINMENT', '', 'NorthWest');

    plotGenericLine(3, 5, 'Service Time on Cloud (sec)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(3, 5, {'Service Time on Cloud';'for Danger Assessment App (sec)'}, 'DANGER_ASSESSMENT', '', 'NorthWest');
    plotGenericLine(3, 5, 'Service Time on Cloud for Navigation App (sec)', 'TRAFFIC_MANAGEMENT', '', 'NorthWest');
    plotGenericLine(3, 5, {'Service Time on Cloud';'for Infotainment App (sec)'}, 'INFOTAINMENT', '', 'NorthWest');

end