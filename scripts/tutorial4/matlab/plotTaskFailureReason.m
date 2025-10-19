function [] = plotTaskFailureReason()

    plotGenericLine(1, 10, 'Failed Task due to VM Capacity (%)', 'ALL_APPS', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(1, 10, {'Failed Task due to VM Capacity';'for Danger Assessment App (%)'}, 'DANGER_ASSESSMENT', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(1, 10, {'Failed Task due to VM Capacity';'for Navigation App (%)'}, 'TRAFFIC_MANAGEMENT', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(1, 10, {'Failed Task due to VM Capacity';'for Infotainment App (%)'}, 'INFOTAINMENT', 'percentage_of_failed', 'NorthWest');

    plotGenericLine(1, 11, 'Failed Task due to Mobility (%)', 'ALL_APPS', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(1, 11, {'Failed Task due to Mobility';'for Danger Assessment App (%)'}, 'DANGER_ASSESSMENT', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(1, 11, {'Failed Task due to Mobility';'for Navigation App (%)'}, 'TRAFFIC_MANAGEMENT', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(1, 11, {'Failed Task due to Mobility';'for Infotainment App (%)'}, 'INFOTAINMENT', 'percentage_of_failed', 'NorthWest');

    plotGenericLine(5, 5, 'Failed Tasks due to WLAN failure (%)', 'ALL_APPS', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 5, {'Failed Tasks due to WLAN failure';'for Danger Assessment App (%)'}, 'DANGER_ASSESSMENT', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 5, {'Failed Tasks due to WLAN failure';'for Navigation App (%)'}, 'TRAFFIC_MANAGEMENT', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 5, {'Failed Tasks due to WLAN failure';'for Infotainment App (%)'}, 'INFOTAINMENT', 'percentage_of_failed', 'NorthWest');

    plotGenericLine(5, 6, 'Failed Tasks due to MAN failure (%)', 'ALL_APPS', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 6, {'Failed Tasks due to MAN failure';'for Danger Assessment App (%)'}, 'DANGER_ASSESSMENT', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 6, {'Failed Tasks due to MAN failure';'for Navigation App (%)'}, 'TRAFFIC_MANAGEMENT', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 6, {'Failed Tasks due to MAN failure';'for Infotainment App (%)'}, 'INFOTAINMENT', 'percentage_of_failed', 'NorthWest');

    plotGenericLine(5, 7, 'Failed Tasks due to WAN failure (%)', 'ALL_APPS', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 7, {'Failed Tasks due to WAN failure';'for Danger Assessment App (%)'}, 'DANGER_ASSESSMENT', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 7, {'Failed Tasks due to WAN failure';'for Navigation App (%)'}, 'TRAFFIC_MANAGEMENT', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 7, {'Failed Tasks due to WAN failure';'for Infotainment App (%)'}, 'INFOTAINMENT', 'percentage_of_failed', 'NorthWest');

end