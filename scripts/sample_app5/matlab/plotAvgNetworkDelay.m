function [] = plotAvgNetworkDelay()
     plotGenericLine(1, 7, 'Average Network Delay (sec)', 'ALL_APPS', 'NorthWest', 0);

     plotGenericLine(5, 1, 'Average WLAN Delay (sec)', 'ALL_APPS', 'NorthWest', 0);

     plotGenericLine(5, 2, 'Average MAN Delay (sec)', 'ALL_APPS', 'NorthWest', 0);

     plotGenericLine(5, 3, 'Average WAN Delay (sec)', 'ALL_APPS', 'NorthWest', 0, 1, 1);

     plotGenericLine(5, 4, 'Average GSM Delay (sec)', 'ALL_APPS', 'NorthWest', 0, 1, 1, 0, [4, 1700]);
end