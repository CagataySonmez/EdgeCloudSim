function [] = plotAvgServiceTime()

    plotGenericLine(1, 5, 'Service Time (sec)', 'ALL_APPS', '', 'SouthEast');
    plotGenericLine(1, 5, {'Service Time for';'Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '', 'SouthEast');
    plotGenericLine(1, 5, 'Service Time for Health App (sec)', 'HEALTH_APP', '', 'SouthEast');
    plotGenericLine(1, 5, 'Service Time for Infotainment App (sec)', 'INFOTAINMENT_APP', '', 'SouthEast');

    plotGenericLine(2, 5, 'Service Time on Edge (sec)', 'ALL_APPS', '', 'SouthEast');
    plotGenericLine(2, 5, {'Service Time on Edge';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '', 'SouthEast');
    plotGenericLine(2, 5, 'Service Time on Edge for Health App (sec)', 'HEALTH_APP', '', 'SouthEast');
    plotGenericLine(2, 5, {'Service Time on Edge';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '', 'SouthEast');

    plotGenericLine(3, 5, 'Service Time on Cloud (sec)', 'ALL_APPS', '', 'NorthWest');
    plotGenericLine(3, 5, {'Service Time on Cloud';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '', 'NorthWest');
    plotGenericLine(3, 5, 'Service Time on Cloud for Health App (sec)', 'HEALTH_APP', '', 'NorthWest');
    plotGenericLine(3, 5, {'Service Time on Cloud';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '', 'NorthWest');

end