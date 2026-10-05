import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AccountRecoveryService } from '../../../core/account-recovery.service';
import { toProblem } from '../../../core/api-error';

type State = 'pending' | 'confirmed' | 'invalid';

/** Подтверждение почты по ссылке из письма. Работает и без входа: ссылка сама по себе доказывает доступ к адресу. */
@Component({
  selector: 'app-confirm-email',
  imports: [RouterLink],
  templateUrl: './confirm-email.html',
  styleUrl: '../auth-page.scss',
})
export class ConfirmEmail implements OnInit {
  private readonly recovery = inject(AccountRecoveryService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly state = signal<State>('pending');
  protected readonly message = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    const token = this.route.snapshot.queryParamMap.get('token');
    void this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
    if (!token) {
      this.fail('В ссылке нет токена. Откройте ссылку из письма целиком.');
      return;
    }
    try {
      await this.recovery.confirmEmail(token);
      this.state.set('confirmed');
    } catch (error) {
      this.fail(toProblem(error).message);
    }
  }

  private fail(message: string): void {
    this.message.set(message);
    this.state.set('invalid');
  }
}
