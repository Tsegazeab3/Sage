#ifndef BYTE_TRACKER_H
#define BYTE_TRACKER_H

#include <vector>
#include <deque>
#include <map>
#include <cfloat> // Added for FLT_MAX
// Eigen removed as we use a custom SimpleKalmanFilter
// #include <eigen3/Eigen/Core>
// #include <eigen3/Eigen/Dense>

#include <vector>
#include <string>

struct Object
{
    float x;
    float y;
    float width;
    float height;
    int label;
    float prob;
    int track_id = -1;
};

// Simple Kalman Filter implementation (Constant Velocity Model)
class SimpleKalmanFilter
{
public:
    SimpleKalmanFilter();
    void initiate(const std::vector<float>& measurement);
    void predict();
    void update(const std::vector<float>& measurement);
    
    std::vector<float> project(const std::vector<float>& mean, const std::vector<float>& covariance);
    
    std::vector<float> _mean; // 8 dimensional (cx, cy, aspect_ratio, height, vx, vy, va, vh)
    std::vector<float> _covariance; // 8x8 flattened
    
    // Standard deviations
    float _std_weight_position;
    float _std_weight_velocity;
};

enum TrackState { New = 0, Tracked, Lost, Removed };

class STrack
{
public:
    STrack(std::vector<float> tlwh, float score, int label);
    ~STrack();

    void activate(SimpleKalmanFilter& kf, int frame_id);
    void re_activate(STrack& new_track, int frame_id, bool new_id = false);
    void update(STrack& new_track, int frame_id);
    void mark_lost();
    void mark_removed();

    std::vector<float> tlwh;
    bool is_activated;
    int track_id;
    int state;
    
    float score;
    int label;
    int start_frame;
    int frame_id;
    int tracklet_len;

    SimpleKalmanFilter* kalman_filter; // Added missing pointer

    std::vector<float> mean;
    std::vector<float> covariance;
    
    // For mapping back
    float _original_tlwh[4];
};

class BYTETracker
{
public:
    BYTETracker(int frame_rate = 30, int track_buffer = 30);
    ~BYTETracker();

    std::vector<Object> update(const std::vector<Object>& objects);

private:
    std::vector<STrack*> joint_stracks(std::vector<STrack*> &tlista, std::vector<STrack> &tlistb);
    std::vector<STrack> joint_stracks(std::vector<STrack> &tlista, std::vector<STrack> &tlistb);

    std::vector<STrack> sub_stracks(std::vector<STrack> &tlista, std::vector<STrack> &tlistb);
    void remove_duplicate_stracks(std::vector<STrack> &resa, std::vector<STrack> &resb, std::vector<STrack> &stracksa, std::vector<STrack> &stracksb);

    void linear_assignment(std::vector<std::vector<float> > &cost_matrix, int cost_matrix_size, int cost_matrix_size_size, std::vector<int> &matches, std::vector<int> &unmatched_a, std::vector<int> &unmatched_b);
    std::vector<std::vector<float> > iou_distance(std::vector<STrack*> &atracks, std::vector<STrack> &btracks, int &dist_size, int &dist_size_size);
    std::vector<std::vector<float> > iou_distance(std::vector<STrack> &atracks, std::vector<STrack> &btracks, int &dist_size, int &dist_size_size);
    double lapjv(const std::vector<std::vector<float> > &cost, std::vector<int> &rowsol, std::vector<int> &colsol, 
        bool extend_cost = false, float cost_limit = FLT_MAX, bool return_cost = false);

private:
    SimpleKalmanFilter kalman_filter;
    
    std::vector<STrack> tracked_stracks;
    std::vector<STrack> lost_stracks;
    std::vector<STrack> removed_stracks;

    int frame_id;
    int track_buffer;
    float track_thresh;
    float high_thresh;
    float match_thresh;
};

#endif // BYTE_TRACKER_H
