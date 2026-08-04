function [] = plotTaskFailureReason()

    plotGenericLine(1, 10, 'Failed Task due to VM Capacity (%)', 'ALL_APPS', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(1, 10, {'Failed Task due to VM Capacity';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(1, 10, {'Failed Task due to VM Capacity';'for Health App (%)'}, 'HEALTH_APP', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(1, 10, {'Failed Task due to VM Capacity';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 'percentage_of_failed', 'NorthWest');

    plotGenericLine(1, 11, 'Failed Task due to Mobility (%)', 'ALL_APPS', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(1, 11, {'Failed Task due to Mobility';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(1, 11, {'Failed Task due to Mobility';'for Health App (%)'}, 'HEALTH_APP', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(1, 11, {'Failed Task due to Mobility';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 'percentage_of_failed', 'NorthWest');

    plotGenericLine(5, 5, 'Failed Tasks due to WLAN failure (%)', 'ALL_APPS', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 5, {'Failed Tasks due to WLAN failure';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 5, {'Failed Tasks due to WLAN failure';'for Health App (%)'}, 'HEALTH_APP', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 5, {'Failed Tasks due to WLAN failure';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 'percentage_of_failed', 'NorthWest');

    plotGenericLine(5, 6, 'Failed Tasks due to MAN failure (%)', 'ALL_APPS', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 6, {'Failed Tasks due to MAN failure';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 6, {'Failed Tasks due to MAN failure';'for Health App (%)'}, 'HEALTH_APP', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 6, {'Failed Tasks due to MAN failure';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 'percentage_of_failed', 'NorthWest');

    plotGenericLine(5, 7, 'Failed Tasks due to WAN failure (%)', 'ALL_APPS', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 7, {'Failed Tasks due to WAN failure';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 7, {'Failed Tasks due to WAN failure';'for Health App (%)'}, 'HEALTH_APP', 'percentage_of_failed', 'NorthWest');
    plotGenericLine(5, 7, {'Failed Tasks due to WAN failure';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 'percentage_of_failed', 'NorthWest');

end