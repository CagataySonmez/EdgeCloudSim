function [] = plotTaskFailureReason()

    plotGenericLine(1, 10, 'Failed Task due to VM Capacity (%)', 'ALL_APPS', 'percentage_of_failed', 'NorthWest');
    
    plotGenericLine(1, 11, 'Failed Task due to Mobility (%)', 'ALL_APPS', 'percentage_of_failed', 'NorthWest');
    
    plotGenericLine(5, 5, 'Failed Tasks due to WLAN (%)', 'ALL_APPS', 'percentage_of_failed', 'NorthWest')

end