function [] = plotAvgNetworkDelay()

    plotGenericResult(1, 7, 'Average Network Delay (sec)', 'ALL_APPS', '');
    plotGenericResult(1, 7, {'Average Network Delay';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '');
    plotGenericResult(1, 7, 'Average Network Delay for Health App (sec)', 'HEALTH_APP', '');
    plotGenericResult(1, 7, {'Average Network Delay';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '');
    plotGenericResult(1, 7, {'Average Network Delay';'for Heavy Comp. App (sec)'}, 'HEAVY_COMP_APP', '');

    plotGenericResult(5, 1, 'Average WLAN Delay (sec)', 'ALL_APPS', '');
    plotGenericResult(5, 1, {'Average WLAN Delay';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '');
    plotGenericResult(5, 1, 'Average WLAN Delay for Health App (sec)', 'HEALTH_APP', '');
    plotGenericResult(5, 1, {'Average WLAN Delay';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '');
    plotGenericResult(5, 1, {'Average WLAN Delay';'for Heavy Comp. App %(sec)'}, 'HEAVY_COMP_APP', '');
end