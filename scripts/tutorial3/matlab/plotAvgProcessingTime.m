function [] = plotAvgProcessingTime()

    plotGenericLine(1, 6, 'Processing Time (sec)', 'ALL_APPS', '', 'SouthEast');
    plotGenericLine(1, 6, 'Processing Time for Augmented Reality App (sec)', 'AUGMENTED_REALITY', '', 'SouthEast');
    plotGenericLine(1, 6, 'Processing Time for Health App (sec)', 'HEALTH_APP', '', 'SouthEast');
    plotGenericLine(1, 6, 'Processing Time for Infotainment App (sec)', 'INFOTAINMENT_APP', '', 'SouthEast');

    plotGenericLine(2, 6, 'Processing Time on Edge (sec)', 'ALL_APPS', '', 'SouthEast');
    plotGenericLine(2, 6, {'Processing Time on Edge';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '', 'SouthEast');
    plotGenericLine(2, 6, {'Processing Time on Edge';'for Health App (sec)'}, 'HEALTH_APP', '', 'SouthEast');
    plotGenericLine(2, 6, {'Processing Time on Edge';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '', 'SouthEast');

    plotGenericLine(3, 6, 'Processing Time on Cloud (sec)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(3, 6, {'Processing Time on Cloud';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '', 'NorthWest');
    plotGenericLine(3, 6, {'Processing Time on Cloud';'for Health App (sec)'}, 'HEALTH_APP', '', 'NorthWest');
    plotGenericLine(3, 6, {'Processing Time on Cloud';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '', 'NorthWest');
    
end