function [] = plotAvgNetworkDelay()

    plotGenericLine(1, 7, 'Average Network Delay (sec)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(1, 7, {'Average Network Delay';'for Danger Assessment App (sec)'}, 'DANGER_ASSESSMENT', '', 'NorthWest');
    plotGenericLine(1, 7, 'Average Network Delay for Navigation App (sec)', 'TRAFFIC_MANAGEMENT', '', 'NorthWest');
    plotGenericLine(1, 7, {'Average Network Delay';'for Infotainment App (sec)'}, 'INFOTAINMENT', '', 'NorthWest');

    plotGenericLine(5, 1, 'Average WLAN Delay (sec)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(5, 1, {'Average WLAN Delay';'for Danger Assessment App (sec)'}, 'DANGER_ASSESSMENT', '', 'NorthWest');
    plotGenericLine(5, 1, 'Average WLAN Delay for Navigation App (sec)', 'TRAFFIC_MANAGEMENT', '', 'NorthWest');
    plotGenericLine(5, 1, {'Average WLAN Delay';'for Infotainment App (sec)'}, 'INFOTAINMENT', '', 'NorthWest');

    plotGenericLine(5, 2, 'Average MAN Delay (sec)', 'ALL_APPS', '', 'NorthEast');
    plotGenericLine(5, 2, {'Average MAN Delay';'for Danger Assessment App (sec)'}, 'DANGER_ASSESSMENT', '', 'NorthEast');
    plotGenericLine(5, 2, 'Average MAN Delay for Navigation App (sec)', 'TRAFFIC_MANAGEMENT', '', 'NorthEast');
    plotGenericLine(5, 2, {'Average MAN Delay';'for Infotainment App (sec)'}, 'INFOTAINMENT', '', 'SouthWest');

    plotGenericLine(5, 3, 'Average WAN Delay (sec)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(5, 3, {'Average WAN Delay';'for Danger Assessment App (sec)'}, 'DANGER_ASSESSMENT', '', 'NorthWest');
    plotGenericLine(5, 3, 'Average WAN Delay for Navigation App (sec)', 'TRAFFIC_MANAGEMENT', '', 'NorthWest');
    plotGenericLine(5, 3, {'Average WAN Delay';'for Infotainment App (sec)'}, 'INFOTAINMENT', '', 'NorthWest');
    
end