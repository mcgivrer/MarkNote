---
title: Proposer un context Enrichi
author: Frédéric Delorme
create_at: 2026-03-21
tags: ai,llm,prompt,context
summary: Proposer un amelioration de la zone de pompt (`PromptInputArea`) en d'intégrant dans le context du prompt  les fichiers ouverts (`DocumentTab`).
---

# Proposer un context Enrichi

title: Proposer un context Enrichi
author: Frédéric Delorme
create_at: 2026-03-21
tags: ai,llm,prompt,context

## But

Proposer un amelioration de la zone de pompt (`PromptInputArea`) en d'intégrant dans le context du prompt  les fichiers ouverts (`DocumentTab`).

## Proposition

Il faut lister autant de boutons que de fichier ouvert, juste au dessus de la zone de saise du prompt.

Ainsi, tous ceux qui sont pressés seront ajoutés au contexte lors de la soumission du prompt au LLM.

## Remarques

 > [!NOTE] le design de bouton utilisé ici devra être très fin avec un texte en police 8pt et un fond clair. Le tour est en pointillé par défaut, si il est activé il devient plein.


> [!NOTE] Un petit '+' dans un carré est affiché sur la partie gauche de chaque bouton. si activé le '+' devien un 'check'.  Su la partie droite, le nom du fichier.


 >[!WARNING] les boutons sont stackés les uns après les autres ligne après ligne si besoin.