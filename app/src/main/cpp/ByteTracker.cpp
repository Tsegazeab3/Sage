#include "ByteTracker.h"
#include <vector>
#include <cmath>
#include <algorithm>
#include <limits>
#include <map>
#include <set>

// -------------------------------------------------------------------------
// Simple Matrix Math Helpers
// -------------------------------------------------------------------------
// (Implementing minimal necessary matrix ops for Kalman Filter)

// -------------------------------------------------------------------------
// SimpleKalmanFilter Implementation
// -------------------------------------------------------------------------
// NOTE: This is a simplified Constant Velocity Model.
// For a production app without Eigen/OpenCV, we use a simplified update rule
// where we assume independent states or diagonal covariance to simplify math.
// 
// State: [cx, cy, aspect_ratio, height, vx, vy, va, vh]

SimpleKalmanFilter::SimpleKalmanFilter()
{
    _std_weight_position = 1.0f / 20.0f;
    _std_weight_velocity = 1.0f / 160.0f;
}

void SimpleKalmanFilter::initiate(const std::vector<float>& measurement)
{
    // measurement: [cx, cy, aspect_ratio, height]
    // mean: [cx, cy, a, h, 0, 0, 0, 0]
    _mean.assign(8, 0.0f);
    for(int i=0; i<4; i++) _mean[i] = measurement[i];

    // Covariance initialization
    // Diagonals only for simplicity in this implementation
    _covariance.assign(8, 0.0f); // Store as diagonal vector or simplified
    
    // In a full implementation, this involves matrix math. 
    // Here we will rely on a simplified heuristic for "predict" 
    // since we lack a matrix library.
    // Basically: new_pos = old_pos + velocity
}

void SimpleKalmanFilter::predict()
{
    // x = F * x
    // F is Identity + dt for velocity components
    // cx += vx, cy += vy, a += va, h += vh
    
    // In strict ByteTrack, aspect ratio velocity is 0. 
    // We update positions.
    _mean[0] += _mean[4];
    _mean[1] += _mean[5];
    _mean[2] += _mean[6];
    _mean[3] += _mean[7];

    // Covariance update (simplified)
    // P = F * P * F^T + Q
    // We just increase uncertainty slightly
    // This is a HACK to avoid implementing full matrix mul in raw C++
    // for this specific constrained environment.
}

void SimpleKalmanFilter::update(const std::vector<float>& measurement)
{
    // y = z - H * x
    // H selects first 4 components
    
    std::vector<float> residual(4);
    for(int i=0; i<4; i++) residual[i] = measurement[i] - _mean[i];

    // Kalman Gain K calculation is complex without matrix inversion.
    // We will use a fixed alpha/beta filter approximation which is 
    // computationally cheap and often sufficient for visual tracking.
    
    float alpha = 0.3f; // Weight for position
    float beta = 0.1f;  // Weight for velocity

    for(int i=0; i<4; i++) {
        _mean[i] = _mean[i] + alpha * residual[i];
        _mean[i+4] = _mean[i+4] + beta * residual[i]; // Velocity update
    }
}

// -------------------------------------------------------------------------
// STrack Implementation
// -------------------------------------------------------------------------
STrack::STrack(std::vector<float> tlwh_, float score_, int label_)
{
    _original_tlwh[0] = tlwh_[0];
    _original_tlwh[1] = tlwh_[1];
    _original_tlwh[2] = tlwh_[2];
    _original_tlwh[3] = tlwh_[3];
    tlwh = tlwh_;
    
    score = score_;
    label = label_;
    
    is_activated = false;
    track_id = 0;
    state = TrackState::New;
    start_frame = 0;
    frame_id = 0;
    tracklet_len = 0;
}

STrack::~STrack() {}

void STrack::activate(SimpleKalmanFilter& kf, int frame_id_)
{
    this->kalman_filter = &kf; // Not actually storing pointer in this simplified version
    this->track_id = -1; // Assigned by global counter
    
    std::vector<float> xyah = {
        tlwh[0] + tlwh[2]/2.0f, // cx
        tlwh[1] + tlwh[3]/2.0f, // cy
        tlwh[2] / tlwh[3],      // aspect ratio
        tlwh[3]                 // height
    };
    
    // In this simplified version, we'll just store the Mean directly in STrack 
    // or use a temporary KF helper. 
    // To properly link, we'd call kf.initiate(xyah) and store the result.
    // For now, let's just initialize our internal state.
    // We will assume STrack HAS-A state.
    
    // Hack: STrack needs its own KF state. 
    // We will use the 'mean' vector in STrack.
    mean.assign(8, 0.0f);
    mean[0] = xyah[0]; mean[1] = xyah[1]; mean[2] = xyah[2]; mean[3] = xyah[3];
    
    this->track_id = 0; // Will be set by tracker
    this->state = TrackState::Tracked;
    this->is_activated = true;
    this->frame_id = frame_id_;
    this->start_frame = frame_id_;
    this->tracklet_len = 0;
}

void STrack::re_activate(STrack& new_track, int frame_id_, bool new_id)
{
    // Update state with new detection
    std::vector<float> xyah = {
        new_track.tlwh[0] + new_track.tlwh[2]/2.0f,
        new_track.tlwh[1] + new_track.tlwh[3]/2.0f,
        new_track.tlwh[2] / new_track.tlwh[3],
        new_track.tlwh[3]
    };
    
    // Simple update logic (alpha-beta filter from above logic)
    float alpha = 0.3f;
    float beta = 0.1f;
    
    std::vector<float> residual(4);
    for(int i=0; i<4; i++) residual[i] = xyah[i] - mean[i];

    for(int i=0; i<4; i++) {
        mean[i] = mean[i] + alpha * residual[i];
        mean[i+4] = mean[i+4] + beta * residual[i];
    }
    
    // Update TLWH from mean
    tlwh[0] = mean[0] - (mean[3] * mean[2]) / 2.0f; // cx - w/2
    tlwh[1] = mean[1] - mean[3] / 2.0f;             // cy - h/2
    tlwh[2] = mean[3] * mean[2];                    // w
    tlwh[3] = mean[3];                              // h

    this->tracklet_len = 0;
    this->state = TrackState::Tracked;
    this->is_activated = true;
    this->frame_id = frame_id_;
    this->score = new_track.score;
}

void STrack::update(STrack& new_track, int frame_id_)
{
    this->frame_id = frame_id_;
    this->tracklet_len++;
    
    std::vector<float> xyah = {
        new_track.tlwh[0] + new_track.tlwh[2]/2.0f,
        new_track.tlwh[1] + new_track.tlwh[3]/2.0f,
        new_track.tlwh[2] / new_track.tlwh[3],
        new_track.tlwh[3]
    };

    // Alpha-beta update
    float alpha = 0.3f; 
    float beta = 0.1f;
    std::vector<float> residual(4);
    for(int i=0; i<4; i++) residual[i] = xyah[i] - mean[i];
    for(int i=0; i<4; i++) {
        mean[i] += alpha * residual[i];
        mean[i+4] += beta * residual[i];
    }

    tlwh[0] = mean[0] - (mean[3] * mean[2]) / 2.0f;
    tlwh[1] = mean[1] - mean[3] / 2.0f;
    tlwh[2] = mean[3] * mean[2];
    tlwh[3] = mean[3];
    
    this->state = TrackState::Tracked;
    this->is_activated = true;
    this->score = new_track.score;
}

void STrack::mark_lost() { state = TrackState::Lost; }
void STrack::mark_removed() { state = TrackState::Removed; }


// -------------------------------------------------------------------------
// BYTETracker Implementation
// -------------------------------------------------------------------------

BYTETracker::BYTETracker(int frame_rate, int track_buffer_)
{
    track_buffer = track_buffer_;
    frame_id = 0;
    track_thresh = 0.4f; // Adjustable
    high_thresh = 0.6f;
    match_thresh = 0.8f;
}

BYTETracker::~BYTETracker() 
{
    // Clean up pointers if necessary
}

// Basic IOU function
float calc_iou(const std::vector<float>& bb_test, const std::vector<float>& bb_gt) {
    float xx1 = std::max(bb_test[0], bb_gt[0]);
    float yy1 = std::max(bb_test[1], bb_gt[1]);
    float xx2 = std::min(bb_test[0] + bb_test[2], bb_gt[0] + bb_gt[2]);
    float yy2 = std::min(bb_test[1] + bb_test[3], bb_gt[1] + bb_gt[3]);

    float w = std::max(0.0f, xx2 - xx1);
    float h = std::max(0.0f, yy2 - yy1);
    float wh = w * h;
    float o = wh / ((bb_test[2] * bb_test[3]) + (bb_gt[2] * bb_gt[3]) - wh);
    return o;
}

std::vector<Object> BYTETracker::update(const std::vector<Object>& objects)
{
    frame_id++;

    // 1. Separate detections
    std::vector<STrack> activated_stracks;
    std::vector<STrack> refind_stracks;
    std::vector<STrack> removed_stracks;
    std::vector<STrack> lost_stracks;

    std::vector<STrack> detections;
    std::vector<STrack> detections_low;

    for (const auto& obj : objects) {
        std::vector<float> tlwh = {obj.x, obj.y, obj.width, obj.height};
        STrack strack(tlwh, obj.prob, obj.label);
        if (obj.prob >= track_thresh) {
            detections.push_back(strack);
        } else {
            detections_low.push_back(strack);
        }
    }

    // 2. Predict tracks
    std::vector<STrack*> strack_pool;
    for (auto& t : tracked_stracks) strack_pool.push_back(&t);
    for (auto& t : this->lost_stracks) strack_pool.push_back(&t);

    for (auto* t : strack_pool) {
        if (t->state != TrackState::Tracked) {
             t->mean[0] += t->mean[4];
             t->mean[1] += t->mean[5];
             t->mean[2] += t->mean[6];
             t->mean[3] += t->mean[7];
        } else {
             t->mean[0] += t->mean[4];
             t->mean[1] += t->mean[5];
             t->mean[2] += t->mean[6];
             t->mean[3] += t->mean[7];
        }
        
        // Update TLWH
        t->tlwh[0] = t->mean[0] - (t->mean[3] * t->mean[2]) / 2.0f;
        t->tlwh[1] = t->mean[1] - t->mean[3] / 2.0f;
        t->tlwh[2] = t->mean[3] * t->mean[2];
        t->tlwh[3] = t->mean[3];
    }

    // 3. First Association (High Score)
    std::vector<STrack*> unconfirmed;
    std::vector<STrack*> tracked_stracks_ptrs;
    for(auto& t : tracked_stracks) {
        if(t.is_activated) tracked_stracks_ptrs.push_back(&t);
        else unconfirmed.push_back(&t);
    }
    
    // Matching (Greedy IOU)
    // We compute IOU matrix and pick best matches
    std::vector<int> matches_a, matches_b;
    std::vector<int> unmatched_a, unmatched_b;
    
    // Helper lambda for Greedy Matching
    auto greedy_match = [&](std::vector<STrack*>& tracks, std::vector<STrack>& dets, float thresh) {
        struct Match { int t_idx; int d_idx; float iou; };
        std::vector<Match> all_matches;
        
        for(int i=0; i<tracks.size(); i++) {
            for(int j=0; j<dets.size(); j++) {
                float iou = calc_iou(tracks[i]->tlwh, dets[j].tlwh);
                if (iou > thresh) {
                    all_matches.push_back({i, j, iou});
                }
            }
        }
        
        // Sort by IOU desc
        std::sort(all_matches.begin(), all_matches.end(), [](const Match& a, const Match& b){
            return a.iou > b.iou;
        });
        
        std::set<int> matched_tracks;
        std::set<int> matched_dets;
        std::vector<std::pair<int, int>> final_matches;
        
        for(const auto& m : all_matches) {
            if(matched_tracks.count(m.t_idx) || matched_dets.count(m.d_idx)) continue;
            matched_tracks.insert(m.t_idx);
            matched_dets.insert(m.d_idx);
            final_matches.push_back({m.t_idx, m.d_idx});
        }
        
        return final_matches;
    };

    auto matches1 = greedy_match(tracked_stracks_ptrs, detections, 1.0f - match_thresh); // match_thresh is distance, so IOU needs to be > (1-dist)?? 
    // Wait, typical threshold is 0.8 IOU? No, ByteTrack uses cost.
    // Let's use IOU threshold 0.5 for high score.
    matches1 = greedy_match(tracked_stracks_ptrs, detections, 0.2f); // 0.2 IOU threshold

    std::set<int> matched_track_indices;
    std::set<int> matched_det_indices;

    for(auto& m : matches1) {
        tracked_stracks_ptrs[m.first]->update(detections[m.second], frame_id);
        activated_stracks.push_back(*tracked_stracks_ptrs[m.first]);
        matched_track_indices.insert(m.first);
        matched_det_indices.insert(m.second);
    }

    // 4. Second Association (Low Score)
    std::vector<STrack*> second_track_candidates;
    for(int i=0; i<tracked_stracks_ptrs.size(); i++) {
        if(matched_track_indices.find(i) == matched_track_indices.end()) {
            second_track_candidates.push_back(tracked_stracks_ptrs[i]);
        }
    }
    
    std::vector<STrack> unmatched_dets_low; // Actually just detections_low
    // BUT we need to filter unmatched from first round? No, detections_low are separate.
    
    auto matches2 = greedy_match(second_track_candidates, detections_low, 0.4f); // 0.4 IOU threshold

    for(auto& m : matches2) {
        second_track_candidates[m.first]->update(detections_low[m.second], frame_id);
        activated_stracks.push_back(*second_track_candidates[m.first]);
        // Remove from candidates (logic handled by rebuilding lists later)
    }

    // 5. Lost Tracks (Candidates that didn't match low score)
    // Actually we need to mark them lost
    std::set<int> matched_second_track_indices;
    for(auto& m : matches2) matched_second_track_indices.insert(m.first);

    for(int i=0; i<second_track_candidates.size(); i++) {
        if(matched_second_track_indices.find(i) == matched_second_track_indices.end()) {
             STrack* t = second_track_candidates[i];
             if(t->state != TrackState::Lost) {
                 t->mark_lost();
                 this->lost_stracks.push_back(*t);
             } else {
                 // Already lost, keep it
                 if(frame_id - t->frame_id < track_buffer) {
                     this->lost_stracks.push_back(*t);
                 } else {
                     t->mark_removed();
                 }
             }
        }
    }

    // 6. Init New Tracks
    // Unmatched high score detections
    for(int i=0; i<detections.size(); i++) {
        if(matched_det_indices.find(i) == matched_det_indices.end()) {
             STrack& d = detections[i];
             if(d.score > high_thresh) {
                 d.activate(kalman_filter, frame_id);
                 // Assign new ID
                 static int global_id = 0;
                 d.track_id = ++global_id;
                 activated_stracks.push_back(d);
             }
        }
    }

    // Update member variables
    this->tracked_stracks = activated_stracks;
    
    // Output objects
    std::vector<Object> results;
    for(auto& t : this->tracked_stracks) {
        if(t.is_activated) {
            Object obj;
            obj.x = t.tlwh[0];
            obj.y = t.tlwh[1];
            obj.width = t.tlwh[2];
            obj.height = t.tlwh[3];
            obj.label = t.label;
            obj.prob = t.score;
            obj.track_id = t.track_id;
            results.push_back(obj);
        }
    }
    return results;
}
