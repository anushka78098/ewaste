#!/usr/bin/env bash
# Runs the end-to-end API test and the email test (and the API test again on SQLite if the driver is in lib/).
cd "$(dirname "$0")"
./build.sh >/dev/null || exit 1
code=0

java -cp "out:lib/*" com.ewaste.ApiTest > /tmp/ewaste_api.txt 2>&1 || code=1
grep -E "FAIL|passed|Storage" /tmp/ewaste_api.txt

java -cp "out:lib/*" com.ewaste.AuctionTest > /tmp/ewaste_auction.txt 2>&1 || code=1
echo "--- recycler auctions ---"; grep -E "FAIL|passed|Storage" /tmp/ewaste_auction.txt

java -cp "out:lib/*" com.ewaste.MailTest > /tmp/ewaste_mail.txt 2>&1 || code=1
echo "--- email ---"; grep -E "FAIL|passed" /tmp/ewaste_mail.txt

java -cp "out:lib/*" com.ewaste.DashboardTest > /tmp/ewaste_dash.txt 2>&1 || code=1
echo "--- dashboards, tracking, drives ---"; grep -E "FAIL|passed" /tmp/ewaste_dash.txt

java -cp "out:lib/*" com.ewaste.DecisionEngineTest > /tmp/ewaste_decision.txt 2>&1 || code=1
echo "--- explainable decision engine ---"; grep -E "FAIL|passed" /tmp/ewaste_decision.txt

java -cp "out:lib/*" com.ewaste.server.ThrottleTest > /tmp/ewaste_throttle.txt 2>&1 || code=1
echo "--- login throttle ---"; grep -E "FAIL|passed" /tmp/ewaste_throttle.txt

if ls lib/sqlite-jdbc*.jar >/dev/null 2>&1; then
  echo "--- same API tests against SQLite ---"
  java -cp "out:lib/*" com.ewaste.ApiTest sqlite > /tmp/ewaste_sqlite.txt 2>&1 || code=1
  grep -E "FAIL|passed|Storage" /tmp/ewaste_sqlite.txt
  java -cp "out:lib/*" com.ewaste.AuctionTest sqlite > /tmp/ewaste_auction_sqlite.txt 2>&1 || code=1
  grep -E "FAIL|passed|Storage" /tmp/ewaste_auction_sqlite.txt
fi
exit $code
