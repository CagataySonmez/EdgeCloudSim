function [] = plotAvgServiceTime()

    plotGenericResult(1, 5, 'Service Time (sec)', 'ALL_APPS', '');
    plotGenericResult(1, 5, {'Service Time for';'Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '');
%    plotGenericResult(1, 5, 'Service Time for Health App (sec)', 'HEALTH_APP', '');
%    plotGenericResult(1, 5, 'Service Time for Infotainment App (sec)', 'INFOTAINMENT_APP', '');
    plotGenericResult(1, 5, {'Service Time for';'Compute Intensive App (sec)'}, 'HEAVY_COMP_APP', '');
    
%    plotGenericResult(2, 5, 'Service Time on Cloudlet (sec)', 'ALL_APPS', '');
%    plotGenericResult(2, 5, {'Service Time on Cloudlet';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '');
%    plotGenericResult(2, 5, 'Service Time on Cloudlet for Health App (sec)', 'HEALTH_APP', '');
%    plotGenericResult(2, 5, {'Service Time on Cloudlet';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '');
%    plotGenericResult(2, 5, {'Service Time on Cloudlet';'for Heavy Comp. App (sec)'}, 'HEAVY_COMP_APP', '');
% 
%    plotGenericResult(3, 5, 'Service Time on Cloud (sec)', 'ALL_APPS', '');
%    plotGenericResult(3, 5, {'Service Time on Cloud';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', '');
%    plotGenericResult(3, 5, 'Service Time on Cloud for Health App (sec)', 'HEALTH_APP', '');
%    plotGenericResult(3, 5, {'Service Time on Cloud';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', '');
%    plotGenericResult(3, 5, {'Service Time on Cloud';'for Heavy Comp. App (sec)'}, 'HEAVY_COMP_APP', '');

end
