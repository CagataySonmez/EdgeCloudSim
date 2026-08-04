function [] = plotApDelay()
    folderPath = getConfiguration(1);
    numOfSimulations = getConfiguration(3);
    stepOfxAxis = getConfiguration(4);
    startOfMobileDeviceLoop = getConfiguration(10);
    stepOfMobileDeviceLoop = getConfiguration(11);
    endOfMobileDeviceLoop = getConfiguration(12);
    numOfMobileDevices = (endOfMobileDeviceLoop - startOfMobileDeviceLoop)/stepOfMobileDeviceLoop + 1;
    placeTypes = {'AP 1 (60 km/h)','Ap 4 (40 km/h)','AP 11 (20 km/h)'};

    results = zeros(size(placeTypes,2),numOfMobileDevices);

    for s=1:numOfSimulations
        indexCounter = 1;
        for i=startOfMobileDeviceLoop:stepOfMobileDeviceLoop:endOfMobileDeviceLoop
            try
                filePath1 = strcat(folderPath,'\ite',int2str(s),'\SIMRESULT_ITS_SCENARIO_AI_BASED_',int2str(i),'DEVICES_AP_UPLOAD_DELAY.log');
                filePath2 = strcat(folderPath,'\ite',int2str(s),'\SIMRESULT_ITS_SCENARIO_AI_BASED_',int2str(i),'DEVICES_AP_DOWNLOAD_DELAY.log');
                readData1 = dlmread(filePath1,';',60,0);
                readData2 = dlmread(filePath2,';',60,0);
                
                for j=1:size(placeTypes,2)
                    results(j,indexCounter) = results(j,indexCounter) + mean(readData1(:,j+1)) + mean(readData2(:,j+1));
                end
            catch err
                error(err)
            end
            indexCounter = indexCounter + 1;
        end
    end
    results = results/numOfSimulations;
  
    types = zeros(1,numOfMobileDevices);
    for i=1:numOfMobileDevices
        types(i)=startOfMobileDeviceLoop+((i-1)*stepOfMobileDeviceLoop);
    end
    
    hFig = figure;
    pos=getConfiguration(7);
    fontSizeArray = getConfiguration(8);
    set(hFig, 'Units','centimeters');
    set(hFig, 'Position',pos);
    set(0,'DefaultAxesFontName','Times New Roman');
    set(0,'DefaultTextFontName','Times New Roman');
    set(0,'DefaultAxesFontSize',fontSizeArray(3));
    
    if(getConfiguration(20) == 1)
        for i=stepOfxAxis:stepOfxAxis:numOfMobileDevices
            xIndex=startOfMobileDeviceLoop+((i-1)*stepOfMobileDeviceLoop);
            
            markers = {':k*',':ko',':ks',':kv'};
            for j=1:size(placeTypes,2)
                plot(xIndex, results(j,i),char(markers(j)),'MarkerFaceColor',getConfiguration(20+j),'color',getConfiguration(20+j));
                hold on;
            end
        end
        
        for j=1:size(placeTypes,2)
            plot(types, results(j,:),':k','color',getConfiguration(20+j),'LineWidth',1.5);
            hold on;
        end
    
        set(gca,'color','none');
    else
        markers = {'-k*','-ko','-ks','-kv'};
        for j=1:size(placeTypes,2)
            plot(types, results(j,:),char(markers(j)),'MarkerFaceColor','w','LineWidth',1.4);
            hold on;
        end
        
        %set(gcf, 'Position',getConfiguration(28));
    end

    lgnd = legend(placeTypes,'Location','NorthWest');
    if(getConfiguration(20) == 1)
        set(lgnd,'color','none');
    end
    
    if(getConfiguration(20) == 1)
        set(lgnd,'color','none');
    end
    
    if(getConfiguration(17) == 0)
        manualXAxisCoefficent = 1;
    end
    
    hold off;
    axis square
    xlabel(getConfiguration(9));
    set(gca,'XTick', startOfMobileDeviceLoop + stepOfMobileDeviceLoop:(stepOfxAxis*stepOfMobileDeviceLoop):endOfMobileDeviceLoop);
    set(gca,'XTickLabel', startOfMobileDeviceLoop + stepOfMobileDeviceLoop/manualXAxisCoefficent:(stepOfxAxis*stepOfMobileDeviceLoop)/manualXAxisCoefficent:endOfMobileDeviceLoop/manualXAxisCoefficent);
    ylabel('Average Network Delay (sec)');
    set(gca,'XLim',[startOfMobileDeviceLoop-5 endOfMobileDeviceLoop+5]);
    
    if(getConfiguration(17) == 1)
        xlim = get(gca,'XLim');
        ylim = get(gca,'YLim');
        text(1.02 * xlim(2), 0.165 * ylim(2), 'x 10^2');
    end
    
    set(get(gca,'Xlabel'),'FontSize',fontSizeArray(1));
    set(get(gca,'Ylabel'),'FontSize',fontSizeArray(1));
    set(lgnd,'FontSize',fontSizeArray(2));
    
    if(getConfiguration(18) == 1)
        set(hFig, 'PaperUnits', 'centimeters');
        set(hFig, 'PaperPositionMode', 'manual');
        set(hFig, 'PaperPosition',[0 0 pos(3) pos(4)]);
        set(gcf, 'PaperSize', [pos(3) pos(4)]); %Keep the same paper size
        filename = strcat(folderPath,'\apDelay');
        saveas(gcf, filename, 'pdf');
    end
end