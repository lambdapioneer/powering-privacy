#!/bin/bash
HTTP_HOST="localhost:5000"  # change if required

# Uncomment these lines enable authentication
# HTTP_USER="user"
# HTTP_PASSWORD="uyaeNiyaighiu4HaingohShee2ie9ahs"  # intentionally hard-coded

cd ../scenarios;
rm /tmp/scenarios.zip;
zip /tmp/scenarios.zip *.scenario;

if [ -z "$HTTP_USER" ]; then 
    curl -F "file=@/tmp/scenarios.zip" http://$HTTP_HOST/energy/scenarios; 
else 
    curl -u $HTTP_USER:$HTTP_PASSWORD -F "file=@/tmp/scenarios.zip" http://$HTTP_HOST/energy/scenarios;
fi;
