function [] = plotLocation()
    folderPath = getConfiguration(1);
    numOfSimulations = getConfiguration(3);
    stepOfxAxis = getConfiguration(4);
    startOfMobileDeviceLoop = getConfiguration(10);
    stepOfMobileDeviceLoop = getConfiguration(11);
    endOfMobileDeviceLoop = getConfiguration(12);
    numOfMobileDevices = (endOfMobileDeviceLoop - startOfMobileDeviceLoop)/stepOfMobileDeviceLoop + 1;
    placeTypes = {'Attractiveness L1','Attractiveness L2','Attractiveness L3'};

    results = zeros(size(placeTypes,2),numOfMobileDevices);

    for s=1:numOfSimulations
        indexCounter = 1;
        for i=startOfMobileDeviceLoop:stepOfMobileDeviceLoop:endOfMobileDeviceLoop
            try
                filePath = strcat(folderPath,'\ite',int2str(s),'\SIMRESULT_SINGLE_TIER_NEXT_FIT_',int2str(i),'DEVICES_LOCATION.log');
                readData = dlmread(filePath,';',1,0);

                for j=1:size(placeTypes,2)
                    results(j,indexCounter) = results(j,indexCounter) + mean(readData(:,j+1));
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
    set(hFig, 'Position',getConfiguration(7));
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
    
    hold off;
    axis square
    xlabel('Number of Devices in the Simulation');
    set(gca,'XTick', (stepOfxAxis*stepOfMobileDeviceLoop):(stepOfxAxis*stepOfMobileDeviceLoop):endOfMobileDeviceLoop);
    ylabel('Average Number of Devices in Related Place');
    %set(gca,'YLim',[2 6]);
end