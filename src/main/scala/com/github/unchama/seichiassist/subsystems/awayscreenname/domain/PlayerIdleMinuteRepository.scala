package com.github.unchama.seichiassist.subsystems.awayscreenname.domain

import cats.effect.Sync
import cats.effect.concurrent.Ref

class PlayerIdleMinuteRepository[F[_]: Sync] {

  private val idleMinuteRepository: Ref[F, IdleMinute] =
    Ref.unsafe[F, IdleMinute](IdleMinute.initial)

  /**
   * @return リポジトリの値を1増加させる作用
   */
  def addOneMinute(): F[Unit] = idleMinuteRepository.update(_.increment)

  /**
   * @return リポジトリの値を初期値に戻す作用
   */
  def reset(): F[Unit] = idleMinuteRepository.set(IdleMinute.initial)

}
