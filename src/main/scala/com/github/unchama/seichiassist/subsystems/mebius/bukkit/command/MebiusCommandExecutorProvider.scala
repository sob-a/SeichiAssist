package com.github.unchama.seichiassist.subsystems.mebius.bukkit.command

import cats.data.Kleisli
import cats.effect.{IO, SyncIO}
import com.github.unchama.contextualexecutor.builder.{ContextualExecutorBuilder, Parsers}
import com.github.unchama.contextualexecutor.executors.BranchedExecutor
import com.github.unchama.contextualexecutor.{ContextualExecutor, PartiallyParsedArgs}
import com.github.unchama.datarepository.bukkit.player.PlayerDataRepository
import com.github.unchama.seichiassist.commands.contextual.builder.BuilderTemplates.playerCommandBuilder
import com.github.unchama.seichiassist.subsystems.mebius.bukkit.codec.BukkitMebiusItemStackCodec
import com.github.unchama.seichiassist.subsystems.mebius.bukkit.command.MebiusCommandExecutorProvider.Messages
import com.github.unchama.seichiassist.subsystems.mebius.domain.property.{
  MebiusForcedMaterial,
  MebiusProperty
}
import com.github.unchama.seichiassist.subsystems.mebius.domain.speech.{
  MebiusSpeech,
  MebiusSpeechStrength
}
import com.github.unchama.seichiassist.subsystems.mebius.service.MebiusSpeechService
import com.github.unchama.targetedeffect.commandsender.MessageEffect
import com.github.unchama.targetedeffect.{SequentialEffect, TargetedEffect, UnfocusedEffect}
import org.bukkit.ChatColor._
import org.bukkit.command.{CommandSender, TabExecutor}
import org.bukkit.entity.Player

class MebiusCommandExecutorProvider(
  implicit serviceRepository: PlayerDataRepository[MebiusSpeechService[SyncIO]]
) {

  import ChildExecutors._

  val executor: TabExecutor = {

    BranchedExecutor(
      Map(
        "nickname" -> ChildExecutors.NicknameCommand.executor,
        "naming" -> namingExecutor,
        "convert" -> convertExecutor
      ),
      whenArgInsufficient = Some(printDescriptionExecutor),
      whenBranchNotFound = Some(printDescriptionExecutor)
    ).asNonBlockingTabExecutor()
  }

  object ChildExecutors {
    private case class MebiusInteractionTemplate(
      effectIfMebiusIsNotWorn: TargetedEffect[Player],
      propertyModifier: MebiusProperty => MebiusProperty,
      additionalEffectsOnModification: MebiusProperty => TargetedEffect[Player]
    ) {

      def effectOn(player: Player): IO[TargetedEffect[Player]] =
        for {
          helmet <- IO {
            player.getInventory.getHelmet
          }
          effect <- IO.pure {
            BukkitMebiusItemStackCodec
              .decodePropertyOfOwnedMebius(player)(helmet)
              .map(propertyModifier) match {
              case Some(newProperty) =>
                SequentialEffect(
                  UnfocusedEffect {
                    player.getInventory.setHelmet {
                      BukkitMebiusItemStackCodec
                        .materialize(newProperty, damageValue = helmet.getDurability)
                    }
                  },
                  additionalEffectsOnModification(newProperty)
                )
              case None => effectIfMebiusIsNotWorn
            }
          }
        } yield effect
    }

    val printDescriptionExecutor: ContextualExecutor = ContextualExecutorBuilder
      .beginConfiguration()
      .execution { _ => IO(Messages.commandDescription) }
      .build()

    val namingExecutor: ContextualExecutor = playerCommandBuilder
      .argumentsParsers(List(Parsers.identity))
      .execution { context =>
        val newName = concatHeadAndRemainingArgs(context.args)
        val player = context.sender

        MebiusInteractionTemplate(
          MessageEffect(s"${RED}命名はMEBIUSを装着して行ってください."),
          _.copy(mebiusName = newName),
          newProperty => {
            val newDisplayName =
              BukkitMebiusItemStackCodec.displayNameOfMaterializedItem(newProperty)
            SequentialEffect(
              MessageEffect(s"$newDisplayName${RESET}に命名しました。"),
              Kleisli.liftF {
                serviceRepository(player)
                  .makeSpeechIgnoringBlockage(
                    newProperty,
                    MebiusSpeech(
                      s"わーい、ありがとう！今日から僕は$newDisplayName${RESET}だ！",
                      MebiusSpeechStrength.Loud
                    )
                  )
                  .toIO
              }
            )
          }
        ).effectOn(player)
      }
      .build()

    val convertExecutor: ContextualExecutor = playerCommandBuilder
      .argumentsParsers(List())
      .execution { context =>
        val mainHand = context.sender.getInventory.getItemInMainHand

        BukkitMebiusItemStackCodec.decodeMebiusProperty(mainHand) match {
          case Some(property) =>
            if (property.level.isMaximum) {
              val newProperty = property.toggleForcedMaterial
              val newItem =
                BukkitMebiusItemStackCodec.materialize(newProperty, mainHand.getDurability)

              val newMaterialName = newProperty.forcedMaterial match {
                case MebiusForcedMaterial.None    => "ダイヤモンド"
                case MebiusForcedMaterial.Leather => "革"
                case MebiusForcedMaterial.Gold    => "金"
                case MebiusForcedMaterial.Iron    => "鉄"
                case MebiusForcedMaterial.Chain   => "チェーン"
              }

              IO.pure {
                SequentialEffect(
                  UnfocusedEffect {
                    context.sender.getInventory.setItemInMainHand(newItem)
                  },
                  MessageEffect(s"メインハンドのメビウスの材質を${newMaterialName}に変換しました！")
                )
              }
            } else {
              IO.pure(MessageEffect("メビウスの見た目を変えるためには、メビウスが最大レベルに到達している必要があります！"))
            }
          case None =>
            IO.pure(MessageEffect("メインハンドに持っているアイテムはメビウスではありません！"))
        }
      }
      .build()

    private def concatHeadAndRemainingArgs(args: PartiallyParsedArgs): String =
      args.parsed.head.toString + " " + args.yetToBeParsed.mkString(" ")

    object NicknameCommand {
      private val resetNicknameExecutor = playerCommandBuilder
        .execution { context =>
          val player = context.sender
          setNicknameOverrideOnMebiusOn(
            player,
            player.getName,
            newName => s"${GREEN}メビウスからの呼び名を${newName}にリセットしました.",
            s"${RED}呼び名のリセットはMEBIUSを装着して行ってください."
          )
        }
        .build()

      private val setNicknameExecutor = playerCommandBuilder
        .argumentsParsers(List(Parsers.identity), onMissingArguments = printDescriptionExecutor)
        .execution { context =>
          val player = context.sender
          setNicknameOverrideOnMebiusOn(
            player,
            concatHeadAndRemainingArgs(context.args),
            newName => s"${GREEN}メビウスからの呼び名を${newName}にセットしました.",
            s"${RED}呼び名の設定はMEBIUSを装着して行ってください."
          )
        }
        .build()

      private def setNicknameOverrideOnMebiusOn(
        player: Player,
        name: String,
        successMessage: String => String,
        errorMessage: String
      ): IO[TargetedEffect[Player]] = {

        MebiusInteractionTemplate(
          MessageEffect(errorMessage),
          _.copy(ownerNicknameOverride = Some(name)),
          newProperty =>
            SequentialEffect(
              MessageEffect(successMessage(name)),
              Kleisli.liftF {
                serviceRepository(player)
                  .makeSpeechIgnoringBlockage(
                    newProperty,
                    MebiusSpeech(
                      s"わーい、ありがとう！今日から君のこと$GREEN$name${RESET}って呼ぶね！",
                      MebiusSpeechStrength.Loud
                    )
                  )
                  .toIO
              }
            )
        ).effectOn(player)
      }

      private val checkNicknameExecutor = playerCommandBuilder
        .execution { context =>
          IO(MessageEffect {
            BukkitMebiusItemStackCodec
              .decodePropertyOfOwnedMebius(context.sender)(
                context.sender.getInventory.getHelmet
              )
              .map(_.ownerNickname)
              .fold {
                s"${RED}呼び名の確認はMEBIUSを装着して行ってください."
              } { name => s"${GREEN}現在のメビウスからの呼び名 : $name" }
          })
        }
        .build()

      val executor: BranchedExecutor = BranchedExecutor(
        Map("reset" -> resetNicknameExecutor, "set" -> setNicknameExecutor),
        whenArgInsufficient = Some(checkNicknameExecutor),
        whenBranchNotFound = Some(checkNicknameExecutor)
      )
    }

  }

}

object MebiusCommandExecutorProvider {

  object Messages {
    val commandDescription: TargetedEffect[CommandSender] =
      MessageEffect {
        List(
          s"$RED[Usage]",
          s"$RED/mebius naming [name]",
          s"$RED  現在頭に装着中のMEBIUSに[name]を命名します。",
          "",
          s"$RED/mebius nickname",
          s"$RED  MEBIUSから呼ばれる名前を表示します",
          "",
          s"$RED/mebius nickname set [name]",
          s"$RED  MEBIUSから呼ばれる名前を[name]に変更します",
          "",
          s"$RED/mebius nickname reset",
          s"$RED  MEBIUSからの呼び名をプレイヤー名(初期設定)に戻します",
          "",
          s"$RED/mebius convert",
          s"$RED  MEBIUSの材質を変換します"
        )
      }
  }

}
