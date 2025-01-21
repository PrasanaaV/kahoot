# Kahoot-Like Quiz App (Android)

A real-time multiplayer quiz application built with **Android (Kotlin)**, **Firebase**, and **Lottie**. Hosts can create and launch quizzes with a unique PIN code. Players join using that PIN, answer questions in real-time, and see final scores on a live scoreboard.

## Table of Contents

1. [Project Overview](#project-overview)
2. [Key Features](#key-features)
3. [Architecture](#architecture)
4. [Tech Stack & Libraries](#tech-stack--libraries)
5. [Installation & Setup](#installation--setup)

---

## Project Overview

This app emulates the popular **Kahoot** platform, enabling two primary roles:
- **Host**: Creates, manages, and launches quizzes.
- **Player**: Joins quizzes via a generated PIN code and answers questions in real-time.

Quizzes are made up of multiple-choice questions. A countdown timer enforces time limits, and players compete to achieve the highest score. Real-time data synchronization is handled by **Firebase Firestore**, ensuring all participants see updates immediately.

---

## Key Features

1. **Authentication**  
   - Login/Register with email & password  
   - Sign in with Google
   - Anonymous sign-in for players (optional, if you choose)

2. **Quiz Creation & Management (Host)**  
   - Create quizzes, add questions, provide multiple-choice options, mark correct answers  
   - Set a quiz to **Open for Join** to generate a unique 6-digit PIN  
   - Launch quiz: the app transitions through each question in real-time  
   - End quiz: displays the final scoreboard

3. **Joining & Answering (Player)**  
   - Enter the 6-digit PIN to join an **open** quiz  
   - Answer questions before the timer ends  
   - View final results on the scoreboard

4. **Real-Time Synchronization**  
   - Firestore snapshot listeners ensure hosts & players see live updates  
   - Confetti animation on the final scoreboard for added engagement

5. **Profile Management**  
   - Hosts can update profile information (e.g., display name)  
   - Password reset for hosts

---

## Architecture

The app follows an **MVVM**-inspired approach:

- **Models**:  
  - `Question`, `Quiz`, `Participant`  
- **ViewModels**:  
  - `HostViewModel` (manages the list of questions locally before creating the quiz)
- **Views (Fragments)**:  
  - **HostFragment**: Create & save quiz questions  
  - **HostLobbyFragment**: Manage participants, open or relaunch quiz  
  - **HostQuizFragment**: Controls the question flow in real-time  
  - **PlayerQuizFragment**: Player UI to answer questions  
  - **ScoreboardFragment**: Final results

---

## Installation & Setup

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/YourUser/KahootClone-Android.git
   git checkout merged
