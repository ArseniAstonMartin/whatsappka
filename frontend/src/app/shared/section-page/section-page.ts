import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { StatePanel, StatePanelKind } from '../state-panel/state-panel';

interface SectionData {
  heading: string;
  kind: StatePanelKind;
  title: string;
  message: string;
}

/** Общая страница раздела. Содержимое раздела подключается отдельно; здесь только заголовок и состояние. */
@Component({
  selector: 'app-section-page',
  imports: [StatePanel, RouterLink],
  templateUrl: './section-page.html',
  styleUrl: './section-page.scss',
})
export class SectionPage {
  protected readonly data = inject(ActivatedRoute).snapshot.data as SectionData;
}
