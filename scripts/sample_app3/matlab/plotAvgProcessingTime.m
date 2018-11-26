function [] = plotAvgProcessingTime()

    plotGenericResult(1, 6, 'Processing Time (sec)', 'ALL_APPS', '');
    plotGenericResult(1, 6, 'Processing Time for Augmented Reality App (sec)', 'AUGMENTED_REALITY', '');
    plotGenericResult(1, 6, 'Processing Time for Health App (sec)', 'HEALTH_APP', '');
    plotGenericResult(1, 6, 'Processing Time for Infotainment App (sec)', 'INFOTAINMENT_APP', '');
    plotGenericResult(1, 6, 'Processing Time for Heavy Comp. App (sec)', 'HEAVY_COMP_APP', '');

    plotGenericResult(2, 6, 'Processing Time on Edge (sec)', 'ALL_APPS', '');
    plotGenericResult(2, 6, {'Processing Time on Edge';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '');
    plotGenericResult(2, 6, {'Processing Time on Edge';'for Health App (sec)'}, 'HEALTH_APP', '');
    plotGenericResult(2, 6, {'Processing Time on Edge';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '');
    plotGenericResult(2, 6, {'Processing Time on Edge';'for Heavy Computation App (sec)'}, 'HEAVY_COMP_APP', '');

    plotGenericResult(4, 6, 'Processing Time on Mobile (sec)', 'ALL_APPS', '');
    plotGenericResult(4, 6, {'Processing Time on Mobile';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '');
    plotGenericResult(4, 6, {'Processing Time on Mobile';'for Health App (sec)'}, 'HEALTH_APP', '');
    plotGenericResult(4, 6, {'Processing Time on Mobile';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '');
    plotGenericResult(4, 6, {'Processing Time on Mobile';'for Heavy Computation App (sec)'}, 'HEAVY_COMP_APP', '');
    
end