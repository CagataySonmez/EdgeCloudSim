function [] = plotDelayReasonAsBar(isEdge)
    folderPath = getConfiguration(1);
    numOfSimulations = getConfiguration(3);
    stepOfxAxis = getConfiguration(4);
    startOfMobileDeviceLoop = getConfiguration(10);
    stepOfMobileDeviceLoop = getConfiguration(11);
    endOfMobileDeviceLoop = getConfiguration(12);
    numOfMobileDevices = (endOfMobileDeviceLoop - startOfMobileDeviceLoop)/stepOfMobileDeviceLoop + 1;

    all_results = zeros(numOfSimulations, numOfMobileDevices, 2);
    
    if ~exist('isEdge','var')
        isEdge = 1;
    end

    for s=1:numOfSimulations
        for j=1:numOfMobileDevices
            try
                mobileDeviceNumber = startOfMobileDeviceLoop + stepOfMobileDeviceLoop * (j-1);
                filePath = strcat(folderPath,'/ite',int2str(s),'/SIMRESULT_DEFAULT_SCENARIO_WORST_FIT_',int2str(mobileDeviceNumber),'DEVICES_ALL_APPS_GENERIC.log');

                readData = dlmread(filePath,';',1,0);
                value1 = 0;
                value2 = 0;
                if(isEdge == 1)
                    value1 = readData(2,6);
                    value2 = readData(5,1);
                else
                    value1 = readData(3,6);
                    value2 = readData(5,3);
                end
                
                all_results(s,j,1) = value1;
                all_results(s,j,2) = value2;
            catch err
                error(err)
            end
        end
    end
    
    if(numOfSimulations == 1)
        results = all_results;
    else
        results = mean(all_results); %still 3d matrix but 1xMxN format
    end
    
    results = squeeze(results); %remove singleton dimensions
    
    types = zeros(1,numOfMobileDevices);
    for i=1:numOfMobileDevices
        types(i)=startOfMobileDeviceLoop + ((i-1)*stepOfMobileDeviceLoop);
    end
    
    hFig=figure;
    pos=getConfiguration(7);
    set(hFig, 'Units','centimeters');
    set(hFig, 'Position',pos);
    set(0,'DefaultAxesFontName','Times New Roman');
    set(0,'DefaultTextFontName','Times New Roman');
    set(0,'DefaultAxesFontSize',12);
    set(0,'DefaultTextFontSize',12);
    

    b = bar(results,'stacked');
    %set(b(1),'LineStyle','--');

    if(isEdge == 1)
        lgnd = legend(b,{'processing time','WLAN delay'},'Location','NorthWest');
        ylabel({'Service Time';'Proportion on Edge (sec)'});
        filename = 'edge_delay_reason';
    else
        lgnd = legend(b,{'processing time','WAN delay'},'Location','NorthWest');
        ylabel({'Service Time';'Proportion on Cloud (sec)'});
        filename = 'cloud_delay_reason';
    end
    
    set(b,{'FaceColor'},{[.45 .45 .45];[.90 .90 .90]});
    
    axis square
    xlabel(getConfiguration(9));
    set(gca,'XTick', 2:2:(endOfMobileDeviceLoop/(stepOfxAxis)));
    set(gca,'XTickLabel', (startOfMobileDeviceLoop*2):(stepOfxAxis*stepOfMobileDeviceLoop*2):endOfMobileDeviceLoop);
    set(gca,'XLim',[0 numOfMobileDevices+1])
    
    set(get(gca,'Xlabel'),'FontSize',12)
    set(get(gca,'Ylabel'),'FontSize',12)
    set(lgnd,'FontSize',12)
    
    if(isEdge)
        set(lgnd,'position',[0.46 0.825 0 0])
    else
        set(lgnd,'position',[0.475 0.825 0 0])
    end
    
    if(getConfiguration(18) == 1)
        set(hFig, 'PaperUnits', 'centimeters');
        set(hFig, 'PaperPositionMode', 'manual');
        set(hFig, 'PaperPosition',[0 0 pos(3) pos(4)]);
        set(gcf, 'PaperSize', [pos(3) pos(4)]); %Keep the same paper size
        filename = strcat(folderPath,'/',filename);
        saveas(gcf, filename, 'pdf');
    end
end