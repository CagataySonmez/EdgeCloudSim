function [] = plotAvgProcessingTime()

    plotGenericResult(1, 6, 'Processing Time (sec)', 'ALL_APPS', 0);
    plotGenericResult(1, 6, 'Processing Time for Augmented Reality App (sec)', 'AUGMENTED_REALITY', 0);
    plotGenericResult(1, 6, 'Processing Time for Health App (sec)', 'HEALTH_APP', 0);
    plotGenericResult(1, 6, 'Processing Time for Infotainment App (sec)', 'INFOTAINMENT_APP', 0);
    plotGenericResult(1, 6, 'Processing Time for Heavy Comp. App (sec)', 'HEAVY_COMP_APP', 0);

    plotGenericResult(2, 6, 'Processing Time on Cloudlet (sec)', 'ALL_APPS', 0);
    plotGenericResult(2, 6, {'Processing Time on Cloudlet';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', 0);
    plotGenericResult(2, 6, {'Processing Time on Cloudlet';'for Health App (sec)'}, 'HEALTH_APP', 0);
    plotGenericResult(2, 6, {'Processing Time on Cloudlet';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', 0);
    plotGenericResult(2, 6, {'Processing Time on Cloudlet';'for Heavy Computation App (sec)'}, 'HEAVY_COMP_APP', 0);

    plotGenericResult(3, 6, 'Processing Time on Cloud (sec)', 'ALL_APPS', 0);
    plotGenericResult(3, 6, {'Processing Time on Cloud';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', 0);
    plotGenericResult(3, 6, {'Processing Time on Cloud';'for Health App (sec)'}, 'HEALTH_APP', 0);
    plotGenericResult(3, 6, {'Processing Time on Cloud';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', 0);
    plotGenericResult(3, 6, {'Processing Time on Cloud';'for Heavy Computation App (sec)'}, 'HEAVY_COMP_APP', 0);
end