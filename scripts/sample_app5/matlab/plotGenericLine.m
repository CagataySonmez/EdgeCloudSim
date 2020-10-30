function [] = plotGenericLine(rowOfset, columnOfset, yLabel, appType, legendPos, calculatePercentage, divisor, ignoreZeroValues, hideLowerValues, hideXAxis)
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
    
    if ~exist('divisor','var')
        divisor = 1;
    end
    
    if ~exist('ignoreZeroValues','var')
        ignoreZeroValues = 0;
    end
    
    if ~exist('hideLowerValues','var')
        hideLowerValues = 0;
    end
    
    if exist('hideXAxis','var')
        hideXAxisStartValue = hideXAxis(2);
        hideXAxisIndex = hideXAxis(1);
    end

    for s=1:numOfSimulations
        for i=1:size(scenarioType,2)
            for j=1:numOfMobileDevices
                try
                    mobileDeviceNumber = startOfMobileDeviceLoop + stepOfMobileDeviceLoop * (j-1);
                    filePath = strcat(folderPath,'\ite',int2str(s),'\SIMRESULT_ITS_SCENARIO_',char(scenarioType(i)),'_',int2str(mobileDeviceNumber),'DEVICES_',appType,'_GENERIC.log');

                    readData = dlmread(filePath,';',rowOfset,0);
                    value = readData(1,columnOfset);
                    if(calculatePercentage==1)
                        readData = dlmread(filePath,';',1,0);
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
        if(ignoreZeroValues == 1)
            results = sum(all_results,1) ./ sum(all_results~=0,1);
            %TODO cahnge NaN to 0
        else
            results = mean(all_results); %still 3d matrix but 1xMxN format
        end
    end
    
    results = squeeze(results); %remove singleton dimensions
    
    for i=1:size(scenarioType,2)
        for j=1:numOfMobileDevices
            if(results(i,j) < hideLowerValues)
                results(i,j) = NaN;
            else
                results(i,j) = results(i,j) / divisor;
            end                
        end
    end
    
    if exist('hideXAxis','var')
        for j=1:numOfMobileDevices
            if(j*stepOfMobileDeviceLoop+startOfMobileDeviceLoop > hideXAxisStartValue)
                results(hideXAxisIndex,j) = NaN;
            end
        end
    end
    
    for i=1:size(scenarioType,2)
        for j=1:numOfMobileDevices
            x=results(i,j);                    % Create Data
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
    pos=getConfiguration(7);
    fontSizeArray = getConfiguration(8);
    set(hFig, 'Units','centimeters');
    set(hFig, 'Position',pos);
    set(0,'DefaultAxesFontName','Times New Roman');
    set(0,'DefaultTextFontName','Times New Roman');
    set(0,'DefaultAxesFontSize',fontSizeArray(3));

    if(getConfiguration(20) == 1)
        for i=1:1:numOfMobileDevices
            xIndex=startOfMobileDeviceLoop+((i-1)*stepOfMobileDeviceLoop);
            
            markers = getConfiguration(50);
            for j=1:size(scenarioType,2)
                plot(xIndex, results(j,i),char(markers(j)),'MarkerFaceColor',getConfiguration(20+j),'color',getConfiguration(20+j));
                hold on;
            end
        end
        
        for j=1:size(scenarioType,2)
            if(getConfiguration(19) == 1)
                errorbar(types, results(j,:), min_results(j,:),max_results(j,:),'-k','color',getConfiguration(20+j),'LineWidth',1);
            else
                plot(types, results(j,:),'-k','color',getConfiguration(20+j),'LineWidth',1);
            end
            hold on;
        end
    
        set(gca,'color','none');
    else
        markers = getConfiguration(40);
        for j=1:size(scenarioType,2)
            if(getConfiguration(19) == 1)
                errorbar(types, results(j,:),min_results(j,:),max_results(j,:),char(markers(j)),'MarkerFaceColor','w','LineWidth',1);
            else
               plot(types, results(j,:),char(markers(j)),'MarkerFaceColor','w');
            end
            hold on;
        end
        
    end
    
    legends = getConfiguration(6);
    lgnd = legend(legends,'Location',legendPos);
    %lgnd.Position=[0.21,0.8,0.2,0.01];
    if(getConfiguration(20) == 1)
        set(lgnd,'color','none');
    end
    
    xCoefficent = 100;
    if(getConfiguration(17) == 0)
        xCoefficent = 1;
    end
    
    hold off;
    axis square
    xlabel(getConfiguration(9));
    step = stepOfxAxis*stepOfMobileDeviceLoop;
    set(gca,'XTick', step:step:endOfMobileDeviceLoop);
    set(gca,'XTickLabel', step/xCoefficent:step/xCoefficent:endOfMobileDeviceLoop/xCoefficent);
    ylabel(yLabel);
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
        filename = strcat(folderPath,'\',int2str(rowOfset),'_',int2str(columnOfset),'_',appType);
        saveas(gcf, filename, 'pdf');
    end
end