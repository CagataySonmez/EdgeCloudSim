function [] = plotGenericResult(rowOfset, columnOfset, yLabel, appType, calculatePercentage)
    folderPath = getConfiguration(1);
    numOfSimulations = getConfiguration(3);
    stepOfxAxis = getConfiguration(4);
    scenarioType = getConfiguration(5);
    startOfMobileDeviceLoop = getConfiguration(10);
    stepOfMobileDeviceLoop = getConfiguration(11);
    endOfMobileDeviceLoop = getConfiguration(12);
    numOfMobileDevices = (endOfMobileDeviceLoop - startOfMobileDeviceLoop)/stepOfMobileDeviceLoop + 1;

    all_results = zeros(numOfSimulations, size(scenarioType,2), numOfMobileDevices);
    min_results = zeros(size(scenarioType,2), numOfMobileDevices);
    max_results = zeros(size(scenarioType,2), numOfMobileDevices);
    
    if ~exist('appType','var')
        appType = 'ALL_APPS';
    end
    
    for s=1:numOfSimulations
        for i=1:size(scenarioType,2)
            for j=1:numOfMobileDevices
                try
                    mobileDeviceNumber = startOfMobileDeviceLoop + stepOfMobileDeviceLoop * (j-1);
                    filePath = strcat(folderPath,'\ite',int2str(s),'\SIMRESULT_',char(scenarioType(i)),'_NEXT_FIT_',int2str(mobileDeviceNumber),'DEVICES_',appType,'_GENERIC.log')
                    
                    readData = dlmread(filePath,';',rowOfset,0);
                    value = readData(1,columnOfset);
                    if(calculatePercentage==1)
                		totalTask = readData(1,1)+readData(1,2);
                        value = (100 * value) / totalTask;
                    end

                    all_results(s,i,j) = value;
                catch err
                    error(err)
                end
            end
        end
    end
    
    if(numOfSimulations == 1)
        results = all_results;
    else
        results = mean(all_results); %still 3d matrix but 1xMxN format
    end
    
    results = squeeze(results); %remove singleton dimensions
    
    for i=1:size(scenarioType,2)
        for j=1:numOfMobileDevices
            x=all_results(:,i,j);                    % Create Data
            SEM = std(x)/sqrt(length(x));            % Standard Error
            ts = tinv([0.05  0.95],length(x)-1);   % T-Score
            CI = mean(x) + ts*SEM;                   % Confidence Intervals

            if(CI(1) < 0)
                CI(1) = 0;
            end

            if(CI(2) < 0)
                CI(2) = 0;
            end

            min_results(i,j) = results(i,j) - CI(1);
            max_results(i,j) = CI(2) - results(i,j);
        end
    end
    
    types = zeros(1,numOfMobileDevices);
    for i=1:numOfMobileDevices
        types(i)=startOfMobileDeviceLoop+((i-1)*stepOfMobileDeviceLoop);
    end
    
    
    hFig = figure;
    set(hFig, 'Position',getConfiguration(7));
    set(0,'DefaultAxesFontName','Times New Roman');
    set(0,'DefaultTextFontName','Times New Roman');
    set(0,'DefaultAxesFontSize',12);
    set(0,'DefaultTextFontSize',12);
    if(getConfiguration(20) == 1)
        for i=stepOfxAxis:stepOfxAxis:numOfMobileDevices
            xIndex=startOfMobileDeviceLoop+((i-1)*stepOfMobileDeviceLoop);
            
            markers = getConfiguration(50);
            for j=1:size(scenarioType,2)
                plot(xIndex, results(j,i),char(markers(j)),'MarkerFaceColor',getConfiguration(20+j),'color',getConfiguration(20+j));
                hold on;
            end
        end
        
        for j=1:size(scenarioType,2)
            if(getConfiguration(19) == 1)
                errorbar(types, results(j,:), min_results(j,:),max_results(j,:),':k','color',getConfiguration(20+j),'LineWidth',1.5);
            else
                plot(types, results(j,:),':k','color',getConfiguration(20+j),'LineWidth',1.5);
            end
            hold on;
        end
    
        set(gca,'color','none');
    else
        markers = getConfiguration(40);
        for j=1:size(scenarioType,2)
            if(getConfiguration(19) == 1)
                errorbar(types, results(j,:),min_max_results(1,j,:),min_max_results(2,j,:),char(markers(j)),'MarkerFaceColor','w','LineWidth',1.4);
            else
               plot(types, results(j,:),char(markers(j)),'MarkerFaceColor','w','LineWidth',1.4);
            end
            hold on;
        end
       
    end
    lgnd = legend(getConfiguration(6),'Location','NorthWest');
    if(getConfiguration(20) == 1)
        set(lgnd,'color','none');
    end
    
    hold off;
    axis square
    xlabel(getConfiguration(9));
    set(gca,'XTick', (stepOfxAxis*stepOfMobileDeviceLoop):(stepOfxAxis*stepOfMobileDeviceLoop):endOfMobileDeviceLoop);
    ylabel(yLabel);
    set(gca,'XLim',[startOfMobileDeviceLoop-5 endOfMobileDeviceLoop+5]);
    
    set(get(gca,'Xlabel'),'FontSize',12)
    set(get(gca,'Ylabel'),'FontSize',12)
    set(lgnd,'FontSize',12)
end