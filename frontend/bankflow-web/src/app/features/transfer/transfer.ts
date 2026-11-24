import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  signal,
  untracked,
} from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { finalize, startWith } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { parseAmount, validateAmount } from '../../core/format/amount-input';
import { AmountPipe } from '../../core/format/amount.pipe';
import { getErrorCode, getFieldErrors, isRetryableError } from '../../core/http/problem-details';
import {
  Account,
  DEFAULT_ACCOUNT_LABEL,
  formatIban,
  getActiveAccounts,
  isPartialPage,
  MAX_ACCOUNT_CHOICES,
  normalizeIban,
  PARTIAL_ACCOUNTS_MESSAGE,
} from '../accounts/accounts.models';
import { AccountsService } from '../accounts/accounts.service';
import { MAX_DESCRIPTION_LENGTH } from '../operations/operations.models';
import { getOperationErrorMessage } from '../operations/operations.service';
import {
  CHOOSE_ACCOUNT_FIRST_MESSAGE,
  IBAN_PATTERN,
  INVALID_IBAN_MESSAGE,
  Transfer,
  TransferRequest,
} from './transfer.models';
import { TransferService } from './transfer.service';

export const UNKNOWN_SOURCE_MESSAGE = 'Ce compte est introuvable ou clôturé.';
const STALE_DATA_CODES: readonly string[] = ['ACCOUNT_CLOSED', 'INSUFFICIENT_FUNDS'];
const FIELDS = ['sourceAccountId', 'destinationIban', 'amount', 'description'] as const;
type TransferField = (typeof FIELDS)[number];

export const CLOSED_SOURCE_MESSAGE = "Ce compte est clôturé : aucun virement n'est possible.";
export const RETRY_HINT = 'Vous pouvez réessayer sans risque de doublon.';

type Step = 'form' | 'review' | 'result';

/** Ce qui est affiché au récapitulatif puis envoyé : figé au passage à l'étape de confirmation. */
interface TransferSummary {
  source: Account;
  destinationIban: string;
  amount: string;
  description: string | null;
}

type Outcome =
  | { kind: 'success'; transfer: Transfer }
  | { kind: 'error'; message: string; isRetryable: boolean };

function validateIban(control: AbstractControl<string>): ValidationErrors | null {
  return IBAN_PATTERN.test(normalizeIban(control.value)) ? null : { iban: INVALID_IBAN_MESSAGE };
}

@Component({
  selector: 'app-transfer',
  imports: [
    AmountPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './transfer.html',
  styleUrl: './transfer.scss',
})
export class TransferPage {
  private readonly transferService = inject(TransferService);
  private readonly accountsService = inject(AccountsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);

  /** Compte source présélectionné (paramètre de requête `from`). */
  readonly from = input<string>();

  protected readonly isAdmin = inject(AuthService).isAdmin;
  protected readonly maxDescription = MAX_DESCRIPTION_LENGTH;
  protected readonly defaultLabel = DEFAULT_ACCOUNT_LABEL;
  protected readonly chooseAccountFirstMessage = CHOOSE_ACCOUNT_FIRST_MESSAGE;
  protected readonly closedSourceMessage = CLOSED_SOURCE_MESSAGE;
  protected readonly retryHint = RETRY_HINT;
  protected readonly formatIban = formatIban;

  protected readonly form = inject(NonNullableFormBuilder).group({
    sourceAccountId: ['', Validators.required],
    destinationIban: ['', [Validators.required, validateIban]],
    amount: ['', validateAmount],
    description: ['', Validators.maxLength(MAX_DESCRIPTION_LENGTH)],
  });
  private readonly sourceId = toSignal(
    this.form.controls.sourceAccountId.valueChanges.pipe(startWith('')),
    { initialValue: '' },
  );

  protected readonly step = signal<Step>('form');
  protected readonly summary = signal<TransferSummary | null>(null);
  protected readonly outcome = signal<Outcome | null>(null);
  protected readonly isSubmitting = signal(false);
  /** Une même intention garde la même clé : la renvoyer après une panne ne double pas le virement. */
  private idempotencyKey: string | null = null;

  /** Le client choisit parmi ses comptes ; l'administrateur part d'un compte précis. */
  protected readonly accounts = rxResource({
    params: () => (this.isAdmin() ? undefined : {}),
    stream: () => this.accountsService.listAccounts(0, MAX_ACCOUNT_CHOICES, true),
  });
  protected readonly adminAccount = rxResource({
    params: () => (this.isAdmin() && this.from() ? { id: this.from() as string } : undefined),
    stream: ({ params }) => this.accountsService.getAccount(params.id),
  });

  protected readonly activeAccounts = computed(() =>
    getActiveAccounts(this.accounts.hasValue() ? this.accounts.value() : undefined),
  );
  protected readonly isPartial = computed(() =>
    isPartialPage(this.accounts.hasValue() ? this.accounts.value() : undefined),
  );
  protected readonly partialMessage = PARTIAL_ACCOUNTS_MESSAGE;
  protected readonly unknownSourceMessage = UNKNOWN_SOURCE_MESSAGE;
  /** Le compte de `from` n'existe pas (admin : 404) ou n'est pas un compte actif du client. */
  protected readonly isSourceUnknown = computed(() => {
    const from = this.from();
    if (!from) {
      return false;
    }
    if (this.isAdmin()) {
      const error = this.adminAccount.error();
      return error instanceof HttpErrorResponse && error.status === 404;
    }
    return this.accounts.hasValue() && !this.activeAccounts().some((a) => a.id === from);
  });
  /** Texte lu par les lecteurs d'écran à l'arrivée d'un succès (la région existe en permanence). */
  protected readonly announcement = computed(() => {
    const result = this.outcome();
    // Un échec est annoncé par son propre `role="alert"` : l'annoncer ici le doublerait.
    return result?.kind === 'success' && this.step() === 'result' ? 'Virement effectué.' : '';
  });
  protected readonly source = computed<Account | null>(() => {
    if (this.isAdmin()) {
      return this.adminAccount.hasValue() ? this.adminAccount.value() : null;
    }
    return this.activeAccounts().find((account) => account.id === this.sourceId()) ?? null;
  });

  /** `from` n'est retenu que s'il désigne un compte connu (actif pour un client). */
  private readonly preselectedId = computed(() => {
    const from = this.from();
    if (!from) {
      return null;
    }
    if (this.isAdmin()) {
      return this.adminAccount.hasValue() ? from : null;
    }
    return this.activeAccounts().some((account) => account.id === from) ? from : null;
  });

  constructor() {
    // Toute modification est une nouvelle demande : elle reçoit sa propre clé.
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.idempotencyKey = null;
    });
    // Le compte source arrive par `from` : on le présélectionne dès qu'il est connu.
    effect(() => {
      const id = this.preselectedId();
      if (id) {
        // Un `from` qui change sur la même route repart d'un virement vierge sur ce compte.
        untracked(() => {
          if (this.form.controls.sourceAccountId.value !== id) {
            this.form.controls.sourceAccountId.setValue(id);
            this.step.set('form');
            this.summary.set(null);
            this.outcome.set(null);
          }
        });
      }
    });
  }

  protected getAccountLabel(account: Account): string {
    return account.label || this.defaultLabel;
  }

  protected goToReview(): void {
    const source = this.source();
    if (this.form.invalid || !source) {
      this.form.markAllAsTouched();
      this.focusSelector(this.getFirstInvalidSelector());
      return;
    }
    const { destinationIban, amount, description } = this.form.getRawValue();
    const parsed = parseAmount(amount);
    if ('error' in parsed) {
      return;
    }
    this.summary.set({
      source,
      destinationIban: normalizeIban(destinationIban),
      amount: parsed.amount,
      description: description.trim() || null,
    });
    this.idempotencyKey ??= crypto.randomUUID();
    this.outcome.set(null);
    this.step.set('review');
    this.focusSelector('#review-title');
  }

  protected editTransfer(): void {
    this.outcome.set(null);
    this.step.set('form');
    this.focusSelector('#form-title');
  }

  protected startNewTransfer(): void {
    this.idempotencyKey = null;
    this.form.reset({ sourceAccountId: this.form.controls.sourceAccountId.value });
    this.outcome.set(null);
    this.summary.set(null);
    this.step.set('form');
    this.focusSelector('#form-title');
  }

  protected confirmTransfer(): void {
    const summary = this.summary();
    if (this.isSubmitting() || !summary) {
      return;
    }
    this.idempotencyKey ??= crypto.randomUUID();
    const request: TransferRequest = {
      sourceAccountId: summary.source.id,
      destinationIban: summary.destinationIban,
      amount: summary.amount,
      description: summary.description,
    };
    this.isSubmitting.set(true);
    this.transferService
      .createTransfer(request, this.idempotencyKey)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false)),
      )
      .subscribe({
        next: (transfer) => {
          this.idempotencyKey = null;
          this.outcome.set({ kind: 'success', transfer });
          this.step.set('result');
          this.reloadBalances();
          this.focusSelector('#result-title');
        },
        error: (error: unknown) => this.showError(error),
      });
  }

  /** Après un refus pour solde ou compte périmé, la liste des comptes est rechargée. */
  private reloadBalances(): void {
    if (this.isAdmin()) {
      this.adminAccount.reload();
    } else {
      this.accounts.reload();
    }
  }

  private showError(error: unknown): void {
    const isRetryable = isRetryableError(error);
    // Un refus définitif (4xx) clôt l'intention : le nouvel envoi aura sa propre clé.
    if (!isRetryable) {
      this.idempotencyKey = null;
    }
    if (STALE_DATA_CODES.includes(getErrorCode(error) ?? '')) {
      this.reloadBalances();
    }
    const fieldErrors = getFieldErrors(error);
    let firstField: TransferField | null = null;
    for (const field of FIELDS) {
      const message = fieldErrors[field];
      if (message) {
        const control = this.form.controls[field];
        control.setErrors({ server: message });
        control.markAsTouched();
        firstField ??= field;
      }
    }
    if (firstField) {
      this.step.set('form');
      this.focusSelector(`[formControlName="${firstField}"]`);
      return;
    }
    this.outcome.set({
      kind: 'error',
      message: getOperationErrorMessage(error, this.isAdmin()),
      isRetryable,
    });
    this.step.set('result');
    this.focusSelector('#result-title');
  }

  private getFirstInvalidSelector(): string {
    const field = FIELDS.find((name) => this.form.controls[name].invalid);
    // Sans compte source valide, le champ est absent pour l'administrateur : le titre prend le focus.
    return field && !(field === 'sourceAccountId' && this.isAdmin())
      ? `[formControlName="${field}"]`
      : '#form-title';
  }

  private focusSelector(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
