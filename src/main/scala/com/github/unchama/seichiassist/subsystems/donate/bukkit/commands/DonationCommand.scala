package com.github.unchama.seichiassist.subsystems.donate.bukkit.commands

import cats.effect.ConcurrentEffect.ops.toAllConcurrentEffectOps
import cats.effect.{ConcurrentEffect, IO, Sync}
import com.github.unchama.contextualexecutor.ContextualExecutor
import com.github.unchama.contextualexecutor.builder.{ContextualExecutorBuilder, Parsers}
import com.github.unchama.contextualexecutor.executors.BranchedExecutor
import com.github.unchama.seichiassist.subsystems.donate.domain.{
  DonatePersistence,
  DonatePremiumEffectPoint,
  Obtained,
  PlayerName
}
import com.github.unchama.targetedeffect.commandsender.MessageEffect
import org.bukkit.ChatColor._
import org.bukkit.command.TabExecutor

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DonationCommand[F[_]: ConcurrentEffect](
  implicit donatePersistence: DonatePersistence[F]
) {

  import cats.implicits._

  private val recordExecutor: ContextualExecutor =
    ContextualExecutorBuilder
      .beginConfiguration()
      .argumentsParsers(
        List(
          Parsers.identity,
          Parsers.integer(MessageEffect(s"${RED}付与するプレミアムエフェクトポイントは整数で指定してください。"))
        )
      )
      .execution { context =>
        val args = context.args.parsed
        val playerName = PlayerName(args.head.toString)
        val donatePoint = DonatePremiumEffectPoint(args(1).asInstanceOf[Int])

        val dateRegex = "[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])".r
        val dateOpt = context.args.yetToBeParsed.headOption
        val isMatchedPattern = dateOpt.forall(date => dateRegex.matches(date))

        val eff = for {
          date <- Sync[F].delay {
            dateOpt match {
              case Some(date) if isMatchedPattern =>
                val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                LocalDate.parse(date, dateTimeFormatter)
              case _ => LocalDate.now()
            }
          }
          _ <- donatePersistence
            .addDonatePremiumEffectPoint(playerName, Obtained(donatePoint, date))
            .whenA(isMatchedPattern)
        } yield {
          if (!isMatchedPattern)
            MessageEffect(s"${RED}購入日はyyyy-MM-ddの形式で指定してください。")
          else
            MessageEffect(
              s"$GREEN${playerName.name}に${donatePoint.value}のプレミアムエフェクトポイントを付与しました。"
            )
        }
        eff.toIO
      }
      .build()

  private val commandDescriptionExecutor: ContextualExecutor =
    ContextualExecutorBuilder
      .beginConfiguration()
      .execution { _ =>
        IO {
          MessageEffect(
            List(
              s"$RED/donation record <プレイヤー名> <ポイント数> <(購入日)>",
              "寄付者用プレミアムエフェクトポイント配布コマンドです(マルチ鯖対応済)",
              "購入日を指定しなければ今日の日付になります。",
              "※購入日はyyyy-MM-ddの形式で指定してください。"
            )
          )
        }
      }
      .build()

  val executor: TabExecutor =
    BranchedExecutor(
      Map("record" -> recordExecutor),
      whenArgInsufficient = Some(commandDescriptionExecutor),
      whenBranchNotFound = Some(commandDescriptionExecutor)
    ).asNonBlockingTabExecutor()

}
