function [] = plotTaskFailureReason()

    plotGenericLine(1, 10, 'Failed Task due to VM Capacity (%)', 'ALL_APPS', 'NorthWest', 1);
    
    plotGenericLine(1, 11, 'Failed Task due to Mobility (%)', 'ALL_APPS', 'NorthWest', 1);
    
    plotGenericLine(5, 5, 'Failed Tasks due to WLAN (%)', 'ALL_APPS', 'NorthWest', 1);

    plotGenericLine(5, 6, 'Failed Tasks due to MAN failure (%)', 'ALL_APPS', 'NorthWest', 1);

    plotGenericLine(5, 7, 'Failed Tasks due to WAN failure (%)', 'ALL_APPS', 'NorthWest', 1);
    
    plotGenericLine(5, 8, 'Failed Tasks due to GSM failure (%)', 'ALL_APPS', 'NorthEast', 1);

end