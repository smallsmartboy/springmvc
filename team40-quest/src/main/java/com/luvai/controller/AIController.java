package com.luvai.controller;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.socket.TextMessage;

import com.google.gson.JsonObject;
import com.luvai.model.AdventureCards.AdventureCard;

public class AIController extends SocketHandler {
	private static final Logger logger = LogManager.getLogger(AIController.class);

	public AIController() {

	}

	public void receiveAICommand(JsonObject jsonObject) throws IOException {

		if (jsonObject.get("AICommand").getAsString().equals("AskToParticipateQuest"))
			AIQuestParticipation(jsonObject);

	}

	public void AIQuestParticipation(JsonObject jsonObject) throws IOException {
		if (gameEngine.getActivePlayer().getAI().doIParticipateQuest()) {
			// to do when tests implemented
		} else {
			logger.info("Player {} denied to participate in quest {} sponsored by {}",
					gameEngine.getActivePlayer().getName(), gameEngine.storyDeck.faceUp.getName(),
					gameEngine.current_quest.sponsor.getName());
			gameEngine.incTurn();
			if (gameEngine.getActivePlayer().equals(gameEngine.current_quest.sponsor)) {
				gameEngine.current_quest.initialPSize = gameEngine.current_quest.participants.size();
				if (gameEngine.current_quest.participants.size() == 0) {

					sendToAllSessions(gameEngine, "NoParticipants");
					String temp = "";
					for (int i = gameEngine.getActivePlayer().getHandSize(); i < 12
							+ gameEngine.current_quest.currentQuest.getStages(); i++) {
						AdventureCard newCard = gameEngine.adventureDeck.flipCard();
						temp += newCard.getStringFile() + ";";
						gameEngine.getActivePlayer().getHand().add(newCard);
					}
					gameEngine.getActivePlayer().session.sendMessage(new TextMessage("SponsorPickup" + temp));
					logger.info("No players participated, sponsor {} is replacing cards used to setup {} quest",
							gameEngine.getActivePlayer().getName(), gameEngine.storyDeck.faceUp.getName());
					String update = gameEngine.getPlayerStats();
					sendToAllSessions(gameEngine, "updateStats" + update);
					return;
				}
				gameEngine.adventureDeck.flipCard();
				gameEngine.getCurrentParticipant().getHand().add(gameEngine.adventureDeck.faceUp);
				String newCardLink = gameEngine.adventureDeck.faceUp.getStringFile();
				gameEngine.getCurrentParticipant().session.sendMessage(new TextMessage("Choose equipment"));
				gameEngine.getCurrentParticipant().session
						.sendMessage(new TextMessage("pickupBeforeStage" + newCardLink));
				gameEngine.current_quest.firstQuestPlayer = gameEngine.getCurrentParticipant();
				return;
			}
			gameEngine.getActivePlayer().session.sendMessage(new TextMessage("AskToParticipate"));
		}
	}

}
