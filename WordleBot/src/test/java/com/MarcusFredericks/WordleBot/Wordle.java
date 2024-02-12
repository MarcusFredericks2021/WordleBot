package com.MarcusFredericks.WordleBot;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;

public class Wordle extends ListenerAdapter {
    private final int maxAttempts;
    private Map<String, Map<String, Object>> userGameStates;

    //Sets maxAttempts to 5, and initializes userGameStates
    public Wordle() {
        this.maxAttempts = 5;
        this.userGameStates = new HashMap<>();
    }

    // Selects a target word randomly from the list populated from WordleWords.txt
    public static String selectTargetWord() {
        List<String> words = new ArrayList<>();
        Random random = new Random();

        try (BufferedReader br = new BufferedReader(new FileReader("WordleWords.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                words.add(line);
            }
            if (!words.isEmpty()) {
                String targetWord = words.get(random.nextInt(words.size()));
                return targetWord.toLowerCase();
            }
        } catch (IOException e) {
            System.err.println("An error occurred while reading the file: " + e.getMessage());
        }
        return "Uncaught error while reading file.";
    }

    // Evaluates userGuess and provides feedback for each character
    public String evaluateGuess(String userId, String userGuess) {
        // Retrieve user data
        Map<String, Object> userData = userGameStates.get(userId);
        String targetWord = (String) userData.get("targetWord");
        HashMap<Integer, String> userGuessChars = new HashMap<>();
        HashMap<Integer, String> targetWordChars = new HashMap<>();
        HashMap<Integer, String> correctChars = new HashMap<>();
        HashMap<Integer, String> feedbackMap = new HashMap<>();
        StringBuilder response = new StringBuilder();

        int remainingAttempts = (int) userData.get("remainingAttempts");

        // Check for invalid guess criteria
        if (userGuess.length() != 5){
            remainingAttempts += 1;
            return "200";
        } else if (userGuess.matches("[a-zA-Z]+") == false) {
            remainingAttempts += 1;
            return "201";
        }
        // Check if the guess is correct
        if (userGuess.equalsIgnoreCase(targetWord)){
            return "100";
        } else {
            // Count occurrences in targetWord
            Map<String, Integer> targetCharCount = Arrays.stream(targetWord.split(""))
                    .map(String::toLowerCase)
                    .collect(Collectors.groupingBy(s -> s, LinkedHashMap::new, Collectors.summingInt(e -> 1)));

        // Populate maps with characters from targetWord and userGuess
        for (int i = 0; i < targetWord.length(); i++) {
            targetWordChars.put(i, String.valueOf(targetWord.charAt(i)));
        }

        for (int i = 0; i < userGuess.length(); i++) {
            userGuessChars.put(i, String.valueOf(userGuess.charAt(i)));
        }

        // Iterate through userGuessChars and compare each entry with the same index,
        // and remove the pairs that have the same value
        Iterator<Map.Entry<Integer, String>> iterator1 = targetWordChars.entrySet().iterator();
        while (iterator1.hasNext()) {
            Map.Entry<Integer, String> entry = iterator1.next();
            Integer index = entry.getKey();
            String value = entry.getValue();

            if (userGuessChars.containsKey(index) && userGuessChars.get(index).equals(value)) {
                correctChars.put(index, value);
                feedbackMap.put(index, value + " is in the correct position.\n");
                iterator1.remove();
                userGuessChars.remove(index, value);
            }
        }

            // Count number of occurrences for each char in correctChars
            Map<String, Integer> correctCharCount = correctChars.values().stream()
                    .collect(Collectors.groupingBy(s -> s, Collectors.summingInt(e -> 1)));

        //iterate through userGuessChars, for each char, compare the value in targetCharCount to the value in correctCharCount
        //if equal, feedback: DNE in word, if correctCharCount value is < targetCharCount value feedback: wrong position, but exists in word
        Iterator<Map.Entry<Integer, String>> iterator2 = userGuessChars.entrySet().iterator();
        while (iterator2.hasNext()) {
            Map.Entry<Integer, String> entry = iterator2.next();
            String character = userGuessChars.get(entry.getKey());
            Integer index = entry.getKey();
            Integer correctCount = correctCharCount.get(character);

            if (correctCount == null && targetWord.contains(character)) { //correctCount =! null && correctCount < targetCharCount.get(character)
                feedbackMap.put((index), character + " is correct but in the wrong position.\n");
                Integer occurrences = 1;
                correctCharCount.put(character, occurrences);
                occurrences ++;
            }else if (correctCount != null && correctCount < targetCharCount.get(character)) {
                feedbackMap.put((index), character + " is correct but in the wrong position.\n");
                Integer occurrences = correctCount + 1;
                correctCharCount.put(character, occurrences);
            }
            else {
                feedbackMap.put((index), character + " is not in the word.\n");
            }
        }
            Iterator<Map.Entry<Integer, String>> iterator3 = feedbackMap.entrySet().iterator();
        while (iterator3.hasNext()) {
            Map.Entry<Integer, String> entry = iterator3.next();
            String feedback = entry.getValue();
            response.append(feedback);
            }
        }
        return response.toString();
    }

    //Sets the start of game state for a new user
    private void startNewGame(String userId, String targetWord) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("targetWord", targetWord);
        userData.put("guessedWords", new ArrayList<String>());
        userData.put("remainingAttempts", maxAttempts);
        userGameStates.put(userId, userData);
    }

    //filters out guesses with incorrect criteria and returns the evaluated feedback.
    //Controls remaining number of attempts
    private String makeGuess(String userId, String userGuess) {
        if(!userGameStates.containsKey(userId)) {
            return  "To start a new game, type '!wordle new game'.";
        }
        Map<String, Object> userData = userGameStates.get(userId);
        String targetWord = (String) userData.get("targetWord");
        int remainingAttempts = (int) userData.get("remainingAttempts");
        userData.put("remainingAttempts", remainingAttempts -= 1);
        String response = evaluateGuess(userId, userGuess);

        if (response.equalsIgnoreCase("100")){
            int attemptsTaken = 5 - remainingAttempts;
            response = "You win! '" + userGuess + "' was the correct word! \nYou guessed the correct word in " + attemptsTaken + " attempt(s)!";
            userGameStates.remove(userId);
        } else {
            if (response.equalsIgnoreCase("200")) {
                userData.put("remainingAttempts", remainingAttempts += 1);
                response = "Guesses need to be 5 letters long";
            } else if (response.equalsIgnoreCase("201")) {
                userData.put("remainingAttempts", remainingAttempts += 1);
                response = "Guesses can only be letters";
            }
            if (remainingAttempts < 0) {
                response += "\nGame over! The correct word was: " + targetWord;
            } else {
                int attemptFeedback = remainingAttempts + 1;
                response += "\nYou have " + attemptFeedback + " attempt(s) remaining.";
            }
            if(userGuess.equalsIgnoreCase("end game")){
                response = "\nGame over! The correct word was: " + targetWord;
            }
        }
        return response;
    }
    //Reads input from all channels
    //If the !wordle prefix is received, will start a new game or make a guess and return the feedback to the user in the channel the message was received in
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        String messageContent = event.getMessage().getContentRaw();
        if(messageContent.startsWith("!wordle")) {
            String userId = event.getAuthor().getId();
            messageContent = messageContent.replace("!wordle", "").trim();
            if (messageContent.equalsIgnoreCase("new game")) {
                String targetWord = selectTargetWord();
                startNewGame(userId, targetWord);
                event.getChannel().sendMessage("Target word has been selected. type a 5 letter word after !wordle to guess! \nYou have 6 attempts remaining").queue();
            } else if (messageContent.equalsIgnoreCase("end game")) {
                String response = makeGuess(userId, messageContent);
                event.getChannel().sendMessage(response).queue();
            } else if (messageContent.equalsIgnoreCase("help")){
                String response = "Start all commands with the '!wordle' prefix. \n'!wordle new game': Start a new game \n'!wordle end game': End the current attempt \n'!wordle help': Command list ";
                event.getChannel().sendMessage(response).queue();
            } else if (userGameStates.containsKey(userId)) {
                String response = makeGuess(userId, messageContent);
                event.getChannel().sendMessage(response).queue();
            } else {
                event.getChannel().sendMessage("To start a new game, type '!wordle new game'.").queue();
            }
        }
    }
}

