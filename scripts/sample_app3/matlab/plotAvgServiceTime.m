function [] = plotAvgServiceTime()

    plotGenericResult(1, 5, 'Service Time (sec)', 'ALL_APPS', '');
    plotGenericResult(1, 5, {'Service Time for';'Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '');
    plotGenericResult(1, 5, 'Service Time for Health App (sec)', 'HEALTH_APP', '');
    plotGenericResult(1, 5, 'Service Time for Infotainment App (sec)', 'INFOTAINMENT_APP', '');
    plotGenericResult(1, 5, {'Service Time for';'Compute Intensive App (sec)'}, 'HEAVY_COMP_APP', '');

    plotGenericResult(2, 5, 'Service Time on Edge (sec)', 'ALL_APPS', '');
    plotGenericResult(2, 5, {'Service Time on Edge';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '');
    plotGenericResult(2, 5, 'Service Time on Edge for Health App (sec)', 'HEALTH_APP', '');
    plotGenericResult(2, 5, {'Service Time on Edge';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '');
    plotGenericResult(2, 5, {'Service Time on Edge';'for Heavy Comp. App (sec)'}, 'HEAVY_COMP_APP', '');

    plotGenericResult(4, 5, 'Service Time on Mobile (sec)', 'ALL_APPS', '');
    plotGenericResult(4, 5, {'Service Time on Mobile';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '');
    plotGenericResult(4, 5, 'Service Time on Mobile for Health App (sec)', 'HEALTH_APP', '');
    plotGenericResult(4, 5, {'Service Time on Mobile';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '');
    plotGenericResult(4, 5, {'Service Time on Mobile';'for Heavy Comp. App (sec)'}, 'HEAVY_COMP_APP', '');

end