export function Spinner({label = 'Загрузка…'}: {label?: string}) {
  return (
    <div className='spinner' role='status' aria-live='polite'>
      <span className='spinner__icon' aria-hidden='true' />
      <span>{label}</span>
    </div>
  );
}
