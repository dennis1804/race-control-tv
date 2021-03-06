package fr.groggy.racecontrol.tv.db.session

import fr.groggy.racecontrol.tv.core.InstantPeriod
import fr.groggy.racecontrol.tv.core.session.SessionRepository
import fr.groggy.racecontrol.tv.db.IdListMapper
import fr.groggy.racecontrol.tv.db.RaceControlTvDatabase
import fr.groggy.racecontrol.tv.f1tv.F1TvChannelId
import fr.groggy.racecontrol.tv.f1tv.F1TvImageId
import fr.groggy.racecontrol.tv.f1tv.F1TvSession
import fr.groggy.racecontrol.tv.f1tv.F1TvSessionId
import fr.groggy.racecontrol.tv.f1tv.F1TvSessionStatus.Companion.Live
import fr.groggy.racecontrol.tv.f1tv.F1TvSessionStatus.Companion.Replay
import fr.groggy.racecontrol.tv.f1tv.F1TvSessionStatus.Companion.Unknown
import fr.groggy.racecontrol.tv.f1tv.F1TvSessionStatus.Companion.Upcoming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSessionRepository @Inject constructor(
    database: RaceControlTvDatabase,
    private val channelIdListMapper: IdListMapper<F1TvChannelId>,
    private val imageIdListMapper: IdListMapper<F1TvImageId>
) : SessionRepository {

    companion object {
        private const val REPLAY = "REPLAY"
        private const val LIVE = "LIVE"
        private const val UPCOMING = "UPCOMING"
    }

    private val dao = database.sessionDao()

    override fun observe(id: F1TvSessionId): Flow<F1TvSession> =
        dao.observeById(id.value)
            .map { toSession(it) }
            .distinctUntilChanged()

    override fun observe(ids: List<F1TvSessionId>): Flow<List<F1TvSession>> =
        dao.observeById(ids.map { it.value })
            .map { sessions -> sessions.map { toSession(it) } }
            .distinctUntilChanged()

    private fun toSession(session: SessionEntity): F1TvSession =
        F1TvSession(
            id = F1TvSessionId(session.id),
            name = session.name,
            status = when (session.status) {
                REPLAY -> Replay
                LIVE -> Live
                UPCOMING -> Upcoming
                else -> Unknown(session.status)
            },
            period = InstantPeriod(
                start = Instant.ofEpochMilli(session.startTime),
                end = Instant.ofEpochMilli(session.endTime)
            ),
            available = session.available,
            images = imageIdListMapper.fromDto(session.images),
            channels = channelIdListMapper.fromDto(session.channels)
        )

    override suspend fun save(session: F1TvSession) {
        val entity = toEntity(session)
        dao.upsert(entity)
    }

    override suspend fun save(sessions: List<F1TvSession>) {
        val entities = sessions.map { toEntity(it) }
        dao.upsert(entities)
    }

    private fun toEntity(session: F1TvSession): SessionEntity =
        SessionEntity(
            id = session.id.value,
            name = session.name,
            status = when (session.status) {
                Replay -> REPLAY
                Live -> LIVE
                Upcoming -> UPCOMING
                is Unknown -> session.status.value
            },
            startTime = session.period.start.toEpochMilli(),
            endTime = session.period.end.toEpochMilli(),
            available = session.available,
            images = imageIdListMapper.toDto(session.images),
            channels = channelIdListMapper.toDto(session.channels)
        )

}