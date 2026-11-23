import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  linkedSignal,
  signal,
  untracked,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
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
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AmountPipe } from '../../core/format/amount.pipe';
import { DateFrPipe } from '../../core/format/date-fr.pipe';
import { parseAmount, validateAmount } from '../../core/format/amount-input';
import { getRangeLabel } from '../../core/format/page-range';
import {
  Account,
  DEFAULT_ACCOUNT_LABEL,
  formatIban,
  PageResponse,
} from '../accounts/accounts.models';
import {
  canCompensate,
  getAmountSign,
  getTransactionLabel,
  MAX_DESCRIPTION_LENGTH,
  OperationRequest,
  Transaction,
  TRANSACTIONS_PAGE_SIZE,
} from '../operations/operations.models';
import {
  ACCOUNT_NOT_FOUND_MESSAGE,
  getErrorCode,
  getFieldErrors,
  getOperationErrorMessage,
  isRetryableError,
  OperationsService,
} from '../operations/operations.service';
import { MAX_REASON_LENGTH, MIN_REASON_LENGTH } from './account-detail.models';
import { AccountDetailService } from './account-detail.service';

export const CLOSED_MESSAGE = 'Le compte a été clôturé.';
export const CANCELLED_MESSAGE = "L'opération a été annulée.";
export const REASON_MESSAGE = 'Le motif doit contenir de 10 à 200 caractères.';
const ZERO_BALANCE = /^-?0+(\.0+)?$/;
/** Après ces refus, l'écran affiche des données périmées : solde et historique sont rechargés. */
const STALE_DATA_CODES: readonly string[] = [
  'ALREADY_COMPENSATED',
  'ACCOUNT_CLOSED',
  'INSUFFICIENT_FUNDS',
];

type OperationKind = 'deposit' | 'withdrawal';

export const SUCCESS_MESSAGES: Record<OperationKind, string> = {
  deposit: 'Le dépôt a été enregistré.',
  withdrawal: 'Le retrait a été enregistré.',
};

function validateReason(control: AbstractControl<string>): ValidationErrors | null {
  const length = control.value.trim().length;
  return length >= MIN_REASON_LENGTH && length <= MAX_REASON_LENGTH ? null : { reason: true };
}

/** État d'un formulaire d'opération : la clé d'idempotence vit autant que l'intention de l'utilisateur. */
interface OperationDraft {
  kind: OperationKind;
  title: string;
  submitLabel: string;
  form: ReturnType<typeof createOperationForm>;
  idempotencyKey: string | null;
  isSubmitting: ReturnType<typeof signal<boolean>>;
  successMessage: ReturnType<typeof signal<string | null>>;
  errorMessage: ReturnType<typeof signal<string | null>>;
}

function createOperationForm(builder: NonNullableFormBuilder) {
  return builder.group({
    amount: ['', validateAmount],
    description: ['', Validators.maxLength(MAX_DESCRIPTION_LENGTH)],
  });
}

@Component({
  selector: 'app-account-detail',
  imports: [
    AmountPipe,
    DateFrPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './account-detail.html',
  styleUrl: './account-detail.scss',
})
export class AccountDetail {
  private readonly accountService = inject(AccountDetailService);
  private readonly operationsService = inject(OperationsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly builder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly id = input.required<string>();

  protected readonly isAdmin = inject(AuthService).isAdmin;
  protected readonly pageSize = TRANSACTIONS_PAGE_SIZE;
  protected readonly getAmountSign = getAmountSign;
  protected readonly defaultLabel = DEFAULT_ACCOUNT_LABEL;
  protected readonly getTransactionLabel = getTransactionLabel;
  protected readonly maxDescription = MAX_DESCRIPTION_LENGTH;
  protected readonly maxReason = MAX_REASON_LENGTH;
  protected readonly reasonMessage = REASON_MESSAGE;
  protected readonly notFoundMessage = ACCOUNT_NOT_FOUND_MESSAGE;
  protected readonly formatIban = formatIban;
  protected readonly getRangeLabel = getRangeLabel;
  protected readonly canCompensate = canCompensate;

  protected readonly account = rxResource({
    params: () => ({ id: this.id() }),
    stream: ({ params }) => this.accountService.getAccount(params.id),
  });
  protected readonly isNotFound = () => {
    const error = this.account.error();
    return error instanceof HttpErrorResponse && error.status === 404;
  };

  protected readonly pageIndex = signal(0);
  protected readonly transactions = rxResource({
    params: () => ({ id: this.id(), page: this.pageIndex() }),
    stream: ({ params }) =>
      this.operationsService.listTransactions(
        { accountId: params.id },
        params.page,
        TRANSACTIONS_PAGE_SIZE,
      ),
  });

  /** Dernière page reçue : elle reste affichée pendant le chargement de la suivante. */
  protected readonly page = linkedSignal<
    PageResponse<Transaction> | undefined,
    PageResponse<Transaction> | undefined
  >({
    source: () => (this.transactions.hasValue() ? this.transactions.value() : undefined),
    computation: (value, previous) => value ?? previous?.value,
  });

  protected readonly drafts: readonly OperationDraft[] = [
    this.createDraft('deposit', 'Déposer de l’argent', 'Déposer'),
    this.createDraft('withdrawal', 'Retirer de l’argent', 'Retirer'),
  ];

  protected readonly isConfirmingClose = signal(false);
  protected readonly isClosing = signal(false);
  protected readonly closeMessage = signal<string | null>(null);
  protected readonly closeError = signal<string | null>(null);

  protected readonly compensatingId = signal<string | null>(null);
  protected readonly reason = new FormControl('', {
    nonNullable: true,
    validators: [validateReason],
  });
  protected readonly isCompensating = signal(false);
  protected readonly compensationMessage = signal<string | null>(null);
  protected readonly compensationError = signal<string | null>(null);

  constructor() {
    // Un autre compte (même composant réutilisé par le routeur) repart d'un écran vierge.
    effect(() => {
      this.id();
      untracked(() => this.resetState());
    });
  }

  protected isZeroBalance(account: Account): boolean {
    return ZERO_BALANCE.test(account.balance);
  }

  protected changePage(pageIndex: number): void {
    this.pageIndex.set(pageIndex);
  }

  protected submitOperation(draft: OperationDraft): void {
    if (draft.isSubmitting()) {
      return;
    }
    if (draft.form.invalid) {
      draft.form.markAllAsTouched();
      this.focusField(draft.kind, draft.form.controls.amount.invalid ? 'amount' : 'description');
      return;
    }
    const { amount, description } = draft.form.getRawValue();
    const parsed = parseAmount(amount);
    if ('error' in parsed) {
      return;
    }
    const request: OperationRequest = {
      amount: parsed.amount,
      description: description.trim() || null,
    };
    // Même clé tant que la demande n'a pas changé : une nouvelle tentative ne double pas l'opération.
    draft.idempotencyKey ??= crypto.randomUUID();
    const send =
      draft.kind === 'deposit'
        ? this.operationsService.deposit.bind(this.operationsService)
        : this.operationsService.withdraw.bind(this.operationsService);
    draft.isSubmitting.set(true);
    draft.successMessage.set(null);
    draft.errorMessage.set(null);
    send(this.id(), request, draft.idempotencyKey)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => draft.isSubmitting.set(false)),
      )
      .subscribe({
        next: () => {
          draft.form.reset();
          draft.idempotencyKey = null;
          draft.successMessage.set(SUCCESS_MESSAGES[draft.kind]);
          this.refresh();
          this.focusField(draft.kind, 'amount');
        },
        error: (error: unknown) => this.showOperationError(draft, error),
      });
  }

  /** Le bouton reste focalisable quand le solde n'est pas nul (aria-disabled) : le clic n'a alors aucun effet. */
  protected askCloseConfirmation(account: Account): void {
    if (!this.isZeroBalance(account)) {
      return;
    }
    this.closeMessage.set(null);
    this.closeError.set(null);
    this.isConfirmingClose.set(true);
    this.focusSelector('.confirm-close');
  }

  protected cancelClose(): void {
    this.isConfirmingClose.set(false);
    this.focusSelector('.close-button');
  }

  protected confirmClose(): void {
    if (this.isClosing()) {
      return;
    }
    this.isClosing.set(true);
    this.closeError.set(null);
    this.accountService
      .closeAccount(this.id())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isClosing.set(false)),
      )
      .subscribe({
        next: () => {
          this.isConfirmingClose.set(false);
          this.closeMessage.set(CLOSED_MESSAGE);
          this.account.reload();
        },
        error: (error: unknown) => {
          this.isConfirmingClose.set(false);
          this.closeError.set(getOperationErrorMessage(error));
          this.focusSelector('.close-button');
        },
      });
  }

  protected startCompensation(transaction: Transaction): void {
    this.reason.reset('');
    this.compensationMessage.set(null);
    this.compensationError.set(null);
    this.compensatingId.set(transaction.id);
    this.focusSelector('.reason-input');
  }

  protected cancelCompensation(): void {
    const id = this.compensatingId();
    this.compensatingId.set(null);
    this.focusSelector(`[data-compensate="${id}"]`);
  }

  protected confirmCompensation(event: Event): void {
    event.preventDefault();
    const id = this.compensatingId();
    if (id === null || this.isCompensating()) {
      return;
    }
    if (this.reason.invalid) {
      this.reason.markAsTouched();
      this.focusSelector('.reason-input');
      return;
    }
    this.isCompensating.set(true);
    this.compensationError.set(null);
    this.operationsService
      .compensate(id, this.reason.value.trim())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isCompensating.set(false)),
      )
      .subscribe({
        next: () => {
          this.compensatingId.set(null);
          this.compensationMessage.set(CANCELLED_MESSAGE);
          this.refresh();
          this.focusSelector('.compensation-status');
        },
        error: (error: unknown) => {
          this.compensationError.set(getOperationErrorMessage(error));
          if (STALE_DATA_CODES.includes(getErrorCode(error) ?? '')) {
            this.refresh();
          }
          this.focusSelector('.reason-input');
        },
      });
  }

  private createDraft(kind: OperationKind, title: string, submitLabel: string): OperationDraft {
    const draft: OperationDraft = {
      kind,
      title,
      submitLabel,
      form: createOperationForm(this.builder),
      idempotencyKey: null,
      isSubmitting: signal(false),
      successMessage: signal<string | null>(null),
      errorMessage: signal<string | null>(null),
    };
    // Une modification du formulaire est une nouvelle demande : elle reçoit une nouvelle clé.
    draft.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      draft.idempotencyKey = null;
    });
    return draft;
  }

  private resetState(): void {
    for (const draft of this.drafts) {
      draft.form.reset();
      draft.idempotencyKey = null;
      draft.successMessage.set(null);
      draft.errorMessage.set(null);
    }
    this.pageIndex.set(0);
    this.compensatingId.set(null);
    this.compensationMessage.set(null);
    this.compensationError.set(null);
    this.isConfirmingClose.set(false);
    this.closeMessage.set(null);
    this.closeError.set(null);
  }

  /** Solde et historique se rafraîchissent après chaque opération réussie. */
  private refresh(): void {
    this.account.reload();
    // Le changement de page déclenche déjà le rechargement : un seul appel suffit.
    if (this.pageIndex() === 0) {
      this.transactions.reload();
    } else {
      this.pageIndex.set(0);
    }
  }

  /** Les erreurs de validation du serveur sont rattachées à leur champ, les autres affichées en haut. */
  private showOperationError(draft: OperationDraft, error: unknown): void {
    // Un refus définitif (4xx) clôt l'intention : le nouvel envoi aura sa propre clé.
    if (!isRetryableError(error)) {
      draft.idempotencyKey = null;
    }
    if (getErrorCode(error) === 'ACCOUNT_CLOSED') {
      this.refresh();
    }
    const fieldErrors = getFieldErrors(error);
    let firstField: 'amount' | 'description' | null = null;
    for (const field of ['amount', 'description'] as const) {
      const message = fieldErrors[field];
      if (message) {
        const control = draft.form.controls[field];
        control.setErrors({ server: message });
        control.markAsTouched();
        firstField ??= field;
      }
    }
    if (firstField) {
      this.focusField(draft.kind, firstField);
      return;
    }
    draft.errorMessage.set(
      isRetryableError(error)
        ? `${getOperationErrorMessage(error)} Vous pouvez réessayer sans risque de doublon.`
        : getOperationErrorMessage(error),
    );
  }

  private focusField(kind: OperationKind, field: 'amount' | 'description'): void {
    this.focusSelector(`[data-form="${kind}"] [formControlName="${field}"]`);
  }

  private focusSelector(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
