package com.github.unchama.seichiassist.commands

import cats.effect.IO
import com.github.unchama.contextualexecutor.builder.{ContextualExecutorBuilder, Parsers}
import com.github.unchama.contextualexecutor.executors.EchoExecutor
import com.github.unchama.seichiassist.SeichiAssist
import com.github.unchama.seichiassist.commands.ContributeCommand.ContributeOperation._
import com.github.unchama.targetedeffect.MessageEffects._
import com.github.unchama.targetedeffect.TargetedEffect.TargetedEffect
import com.github.unchama.util.kotlin2scala.SuspendingMethod
import enumeratum._
import org.bukkit.ChatColor._
import org.bukkit.command.{CommandSender, TabExecutor}

object ContributeCommand {
  sealed trait ContributeOperation extends EnumEntry
  object ContributeOperation extends Enum[ContributeOperation] {
    val values = findValues

    case object ADD extends ContributeOperation
    case object REMOVE extends ContributeOperation
  }

  @SuspendingMethod private def addContributionPoint(targetPlayerName: String, point: Int): TargetedEffect[CommandSender] = {
    SeichiAssist.databaseGateway.playerDataManipulator
      .addContributionPoint(targetPlayerName, point)
      .map(_ => {
        val operationResponse =
          if (point >= 0) {
            s"${GREEN}${targetPlayerName}に貢献度ポイントを${point}追加しました"
          } else {
            s"${GREEN}${targetPlayerName}の貢献度ポイントを${point}減少させました"
          }
        operationResponse.asMessageEffect()
      })
      .merge
  }

  private val printHelpMessageExecutor = new EchoExecutor(
    List(
      s"${YELLOW}${BOLD}[コマンドリファレンス]",
      s"${RED}/contribute add [プレイヤー名] [増加分ポイント]",
      "指定されたプレイヤーの貢献度ptを指定分増加させます",
      s"${RED}/contribute remove [プレイヤー名] [減少分ポイント]",
      "指定されたプレイヤーの貢献度ptを指定分減少させます(入力ミス回避用)"
    ).asMessageEffect()
  )

  private val operationParser = Parsers.fromOptionParser(
    ContributeOperation.withNameLowercaseOnlyOption,
    "操作はadd/removeで与えてください。".asMessageEffect()
  )

  private val pointParser = Parsers.nonNegativeInteger(
    s"${RED}増加分ポイントは0以上の整数を指定してください。".asMessageEffect()
  )

  val executor: TabExecutor = ContextualExecutorBuilder.beginConfiguration()
    .argumentsParsers(
      List(operationParser, pointParser),
      onMissingArguments = printHelpMessageExecutor
    )
    .execution { context =>
      val operation = context.args.parsed[0].asInstanceOf[ContributeOperation]
      val targetPlayerName = context.args.parsed[1].asInstanceOf[String]
      val point = context.args.parsed[2].asInstanceOf[Int]

      operation match {
        case ADD => IO {
          addContributionPoint(targetPlayerName, point)
        }
        case REMOVE => IO {
          addContributionPoint(targetPlayerName, -point)
        }
      }
    }
    .build()
    .asNonBlockingTabExecutor()
}
