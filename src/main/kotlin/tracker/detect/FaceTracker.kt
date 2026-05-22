package tracker.detect

/**
 * Assigns stable integer IDs to detected faces across frames.
 * Matching: greedy by IoU (primary), centroid distance (fallback).
 * Tracks are held for up to [maxMissedFrames] frames after last detection
 * so IDs survive brief occlusions.
 */
class FaceTracker(private val maxMissedFrames: Int = 5) {

    private data class Track(val id: Int, val face: FaceDetection, val missedFrames: Int = 0)

    private var nextId = 0
    private var tracks: List<Track> = emptyList()

    fun update(detections: List<FaceDetection>): List<TrackedFace> {
        val matchedTracks = mutableSetOf<Int>()
        val matchedDets = mutableSetOf<Int>()
        val assignments = mutableMapOf<Int, Int>() // trackIdx -> detIdx

        // Phase 1: greedy IoU matching
        data class Pair(val ti: Int, val di: Int, val score: Float)
        val pairs = buildList {
            for (ti in tracks.indices)
                for (di in detections.indices) {
                    val s = iou(tracks[ti].face, detections[di])
                    if (s >= IOU_THRESHOLD) add(Pair(ti, di, s))
                }
        }.sortedByDescending { it.score }

        for (p in pairs) {
            if (p.ti !in matchedTracks && p.di !in matchedDets) {
                assignments[p.ti] = p.di
                matchedTracks += p.ti
                matchedDets += p.di
            }
        }

        // Phase 2: centroid fallback for unmatched tracks
        for (ti in tracks.indices) {
            if (ti in matchedTracks) continue
            val track = tracks[ti]
            val threshold = maxOf(track.face.boxW, track.face.boxH) * 1.5f
            val best = detections.indices
                .filter { it !in matchedDets }
                .minByOrNull { centroidDist2(track.face, detections[it]) }
            if (best != null && centroidDist2(track.face, detections[best]) < threshold * threshold) {
                assignments[ti] = best
                matchedTracks += ti
                matchedDets += best
            }
        }

        val updated = mutableListOf<Track>()

        for ((ti, di) in assignments)
            updated += tracks[ti].copy(face = detections[di], missedFrames = 0)

        for (ti in tracks.indices) {
            if (ti in matchedTracks) continue
            val t = tracks[ti]
            if (t.missedFrames + 1 <= maxMissedFrames)
                updated += t.copy(missedFrames = t.missedFrames + 1)
        }

        for (di in detections.indices) {
            if (di !in matchedDets)
                updated += Track(id = nextId++, face = detections[di])
        }

        tracks = updated
        return updated.filter { it.missedFrames == 0 }.map { TrackedFace(it.id, it.face) }
    }

    private companion object {
        const val IOU_THRESHOLD = 0.3f

        fun iou(a: FaceDetection, b: FaceDetection): Float {
            val xA = maxOf(a.boxX, b.boxX)
            val yA = maxOf(a.boxY, b.boxY)
            val xB = minOf(a.boxX + a.boxW, b.boxX + b.boxW)
            val yB = minOf(a.boxY + a.boxH, b.boxY + b.boxH)
            if (xB <= xA || yB <= yA) return 0f
            val inter = (xB - xA) * (yB - yA)
            val union = a.boxW * a.boxH + b.boxW * b.boxH - inter
            return if (union <= 0f) 0f else inter / union
        }

        fun centroidDist2(a: FaceDetection, b: FaceDetection): Float {
            val dx = a.centerX - b.centerX
            val dy = a.centerY - b.centerY
            return dx * dx + dy * dy
        }
    }
}
