function [] = plotAvgNetworkDelay()

    plotGenericResult(1, 7, 'Average Network Delay (sec)', 'ALL_APPS', 0);
    plotGenericResult(1, 7, {'Average Network Delay';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', 0);
    plotGenericResult(1, 7, 'Average Network Delay for Health App (sec)', 'HEALTH_APP', 0);
    plotGenericResult(1, 7, {'Average Network Delay';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', 0);
    plotGenericResult(1, 7, {'Average Network Delay';'for Heavy Comp. App (sec)'}, 'HEAVY_COMP_APP', 0);
    
    plotGenericResult(2, 7, 'Average WLAN Delay (sec)', 'ALL_APPS', 0);
    plotGenericResult(2, 7, {'Average WLAN Delay';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', 0);
    plotGenericResult(2, 7, 'Average WLAN Delay for Health App (sec)', 'HEALTH_APP', 0);
    plotGenericResult(2, 7, {'Average WLAN Delay';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', 0);
    plotGenericResult(2, 7, {'Average WLAN Delay';'for Heavy Comp. App %(sec)'}, 'HEAVY_COMP_APP', 0);
    
    plotGenericResult(3, 7, 'Average WAN Delay (sec)', 'ALL_APPS', 0);
    plotGenericResult(3, 7, {'Average WAN Delay';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', 0);
    plotGenericResult(3, 7, 'Average WAN Delay for Health App (sec)', 'HEALTH_APP', 0);
    plotGenericResult(3, 7, {'Average WAN Delay';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', 0);
    plotGenericResult(3, 7, {'Average WAN Delay';'for Heavy Comp. App (sec)'}, 'HEAVY_COMP_APP', 0);
end