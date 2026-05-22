package tracker.detect

/**
 * EN: Pairs a stable integer [id] (assigned by [FaceTracker]) with the raw [FaceDetection]
 * for the current frame. IDs are stable across frames so the UI selection survives
 * brief occlusions and re-entries.
 *
 * RU: Связывает стабильный integer [id] (назначаемый [FaceTracker]) с сырым результатом
 * детекции [FaceDetection] для текущего кадра. ID стабильны между кадрами, поэтому
 * выбор в UI сохраняется при кратковременных перекрытиях и повторных появлениях.
 *
 * @param id        stable face identity / стабильный идентификатор лица
 * @param detection raw detection data for this frame / сырые данные детекции для данного кадра
 */
data class TrackedFace(val id: Int, val detection: FaceDetection)
