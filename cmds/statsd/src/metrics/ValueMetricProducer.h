/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <utils/threads.h>
#include <list>
#include "../condition/ConditionTracker.h"
#include "../external/PullDataReceiver.h"
#include "../external/StatsPullerManager.h"
#include "CountAnomalyTracker.h"
#include "MetricProducer.h"
#include "frameworks/base/cmds/statsd/src/stats_log.pb.h"
#include "frameworks/base/cmds/statsd/src/statsd_config.pb.h"

namespace android {
namespace os {
namespace statsd {

class ValueMetricProducer : public virtual MetricProducer, public virtual PullDataReceiver {
public:
    ValueMetricProducer(const ValueMetric& valueMetric, const int conditionIndex,
                        const sp<ConditionWizard>& wizard);

    virtual ~ValueMetricProducer();

    void onConditionChanged(const bool condition, const uint64_t eventTime) override;

    void finish() override;

    StatsLogReport onDumpReport() override;

    void onSlicedConditionMayChange(const uint64_t eventTime);

    void onDataPulled(const std::vector<std::shared_ptr<LogEvent>>& data) override;
    // TODO: Implement this later.
    size_t byteSize() override{return 0;};

    // TODO: Implement this later.
    virtual void notifyAppUpgrade(const string& apk, const int uid, const int version) override{};
    // TODO: Implement this later.
    virtual void notifyAppRemoved(const string& apk, const int uid) override{};

protected:
    void onMatchedLogEventInternal(const size_t matcherIndex, const HashableDimensionKey& eventKey,
                                   const std::map<std::string, HashableDimensionKey>& conditionKey,
                                   bool condition, const LogEvent& event,
                                   bool scheduledPull) override;

private:
    const ValueMetric mMetric;

    StatsPullerManager& mStatsPullerManager = StatsPullerManager::GetInstance();

    Mutex mLock;

    const int mPullCode;

    // internal state of a bucket.
    typedef struct {
        std::vector<std::pair<long, long>> raw;
    } Interval;

    std::unordered_map<HashableDimensionKey, Interval> mCurrentSlicedBucket;
    // If condition is true and pulling on schedule, the previous bucket value needs to be carried
    // over to the next bucket.
    std::unordered_map<HashableDimensionKey, Interval> mNextSlicedBucket;

    // Save the past buckets and we can clear when the StatsLogReport is dumped.
    std::unordered_map<HashableDimensionKey, std::vector<ValueBucketInfo>> mPastBuckets;

    long get_value(const LogEvent& event);

    void flush_if_needed(const uint64_t eventTimeNs);
};

}  // namespace statsd
}  // namespace os
}  // namespace android
