#!/bin/sh

python data_convertor.py config.json edge classifier train
python data_convertor.py config.json edge classifier test
python data_convertor.py config.json edge regression train
python data_convertor.py config.json edge regression test
python data_convertor.py config.json cloud_rsu classifier train
python data_convertor.py config.json cloud_rsu classifier test
python data_convertor.py config.json cloud_rsu regression train
python data_convertor.py config.json cloud_rsu regression test
python data_convertor.py config.json cloud_gsm classifier train
python data_convertor.py config.json cloud_gsm classifier test
python data_convertor.py config.json cloud_gsm regression train
python data_convertor.py config.json cloud_gsm regression test 
