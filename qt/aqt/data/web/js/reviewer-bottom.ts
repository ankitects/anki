/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

/* eslint
@typescript-eslint/no-unused-vars: "off",
*/

let time: number; // set in python code
let timerStopped = false;

let maxTime = 0;

function updateTime(): void {
    const timeNode = document.getElementById("time");
    if (maxTime === 0) {
        timeNode.textContent = "";
        return;
    }
    time = Math.min(maxTime, time);
    const m = Math.floor(time / 60);
    const s = time % 60;
    const sStr = String(s).padStart(2, "0");
    const timeString = `${m}:${sStr}`;

    if (maxTime === time) {
        timeNode.innerHTML = `<font color=red>${timeString}</font>`;
    } else {
        timeNode.textContent = timeString;
    }
}

let intervalId: number | undefined;

function showQuestion(txt: string, maxTime_: number): void {
    showAnswer(txt);
    time = 0;
    maxTime = maxTime_;
    updateTime();

    if (intervalId !== undefined) {
        clearInterval(intervalId);
    }

    intervalId = setInterval(function() {
        if (!timerStopped) {
            time += 1;
            updateTime();
        }
    }, 1000);
    
    // NOVO: Esconder campo de anotações quando mostrar pergunta
    hideAnnotations();
}

function showAnswer(txt: string, stopTimer = false): void {
    document.getElementById("middle").innerHTML = txt;
    timerStopped = stopTimer;
    
    // NOVO: Mostrar campo de anotações quando mostrar resposta
    showAnnotations();
}

function selectedAnswerButton(): string {
    const node = document.activeElement as HTMLElement;
    if (!node) {
        return;
    }
    return node.dataset.ease;
}

// ==================== NOVO CÓDIGO ====================

/**
 * Mostra o campo de anotações após o usuário ver a resposta
 */
function showAnnotations(): void {
    const annotationsField = document.getElementById("annotations-field");
    if (annotationsField) {
        annotationsField.style.display = "block";
        // Animação suave
        annotationsField.style.opacity = "0";
        setTimeout(() => {
            annotationsField.style.opacity = "1";
        }, 10);
    }
}

/**
 * Esconde o campo de anotações quando mostrar nova pergunta
 */
function hideAnnotations(): void {
    const annotationsField = document.getElementById("annotations-field");
    if (annotationsField) {
        annotationsField.style.display = "none";
    }
}

/**
 * Cria e injeta o campo de anotações no DOM
 * Chamado pelo código Python quando há anotações disponíveis
 */
function displayAnnotations(annotationsHtml: string): void {
    // Remove campo anterior se existir
    const existingField = document.getElementById("annotations-field");
    if (existingField) {
        existingField.remove();
    }
    
    // Cria novo campo de anotações
    const annotationsContainer = document.createElement("div");
    annotationsContainer.id = "annotations-field";
    annotationsContainer.className = "annotations-container";
    annotationsContainer.style.display = "none"; // Escondido inicialmente
    
    annotationsContainer.innerHTML = `
        <div class="annotations-header">📝 Anotações</div>
        <div class="annotations-content">${annotationsHtml}</div>
    `;
    
    // Adiciona ao final do card
    const middleElement = document.getElementById("middle");
    if (middleElement) {
        middleElement.appendChild(annotationsContainer);
    }
}
