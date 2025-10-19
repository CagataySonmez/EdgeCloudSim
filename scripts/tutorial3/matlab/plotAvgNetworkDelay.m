function [] = plotAvgNetworkDelay()

    plotGenericLine(1, 7, 'Average Network Delay (sec)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(1, 7, {'Average Network Delay';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '', 'NorthWest');
    plotGenericLine(1, 7, 'Average Network Delay for Health App (sec)', 'HEALTH_APP', '', 'NorthWest');
    plotGenericLine(1, 7, {'Average Network Delay';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '', 'NorthWest');

    plotGenericLine(5, 1, 'Average WLAN Delay (sec)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(5, 1, {'Average WLAN Delay';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '', 'NorthWest');
    plotGenericLine(5, 1, 'Average WLAN Delay for Health App (sec)', 'HEALTH_APP', '', 'NorthWest');
    plotGenericLine(5, 1, {'Average WLAN Delay';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '', 'NorthWest');

    plotGenericLine(5, 2, 'Average MAN Delay (sec)', 'ALL_APPS', '', 'NorthEast');
    plotGenericLine(5, 2, {'Average MAN Delay';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '', 'NorthEast');
    plotGenericLine(5, 2, 'Average MAN Delay for Health App (sec)', 'HEALTH_APP', '', 'NorthEast');
    plotGenericLine(5, 2, {'Average MAN Delay';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '', 'SouthWest');

    plotGenericLine(5, 3, 'Average WAN Delay (sec)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(5, 3, {'Average WAN Delay';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '', 'NorthWest');
    plotGenericLine(5, 3, 'Average WAN Delay for Health App (sec)', 'HEALTH_APP', '', 'NorthWest');
    plotGenericLine(5, 3, {'Average WAN Delay';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '', 'NorthWest');
    
end