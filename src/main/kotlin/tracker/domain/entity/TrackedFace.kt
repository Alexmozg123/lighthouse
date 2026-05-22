package tracker.domain.entity

/**
 * EN: Pairs a stable integer [id] (assigned by [tracker.domain.usecase.FaceTracker]) with
 * the raw [FaceDetection] for the current frame. IDs survive brief occlusions so the UI
 * selection is not lost when a face temporarily disappears.
 *
 * RU: Связывает стабильный integer [id] (назначаемый [tracker.domain.usecase.FaceTracker])
 * с сырым результатом детекции [FaceDetection] для текущего кадра. ID сохраняются при
 * кратковременных перекрытиях, чтобы выбор в UI не терялся.
 *
 * @param id        stable face identity / стабильный идентификатор лица
 * @param detection raw detection data for this frame / сырые данные детекции для данного кадра
 */
data class TrackedFace(val id: Int, val detection: FaceDetection)
